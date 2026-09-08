package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.DeferredTransitionState
import org.jetbrains.compose.swing.animation.core.ExperimentalDeferredTransitionApi
import org.jetbrains.compose.swing.animation.core.rememberTransition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.WindowState
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Component
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalDeferredTransitionApi::class)
class AnimatedVisibilityScaleContractTest {
    @Test
    fun `a receiverless scale enter is rejected before a hidden container is emitted`() {
        assertFailsWith<IllegalArgumentException> {
            runComposeSwingTest {
                setContent {
                    AnimatedVisibility(visible = false, enter = scaleIn()) { Block() }
                }
            }
        }
    }

    @Test
    fun `a receiverless scale exit is rejected while its content is visible`() {
        assertFailsWith<IllegalArgumentException> {
            runComposeSwingTest {
                setContent {
                    AnimatedVisibility(
                        visible = true,
                        enter = EnterTransition.None,
                        exit = scaleOut(),
                    ) { Block() }
                }
            }
        }
    }

    @Test
    fun `a transition scale is accepted under a Foundation parent`() =
        runComposeSwingTest {
            setContent {
                Box {
                    updateTransition(false).AnimatedVisibility(
                        visible = { it },
                        enter = scaleIn(),
                    ) { Block() }
                }
            }
            assertEquals(1, root.componentCount)
        }

    @Test
    fun `a captured foundation scope cannot make a native parent support container scale`() {
        var foundationScope: ConstrainedScope? = null
        val failure =
            assertFailsWith<IllegalStateException> {
                runComposeSwingTest {
                    setContent {
                        Box { foundationScope = this }
                        Panel {
                            val transition = updateTransition(true)
                            with(checkNotNull(foundationScope)) {
                                transition.AnimatedVisibility(
                                    visible = { it },
                                    enter = scaleIn(),
                                ) { Block() }
                            }
                        }
                    }
                }
            }
        assertTrue(
            failure.message.orEmpty().contains(
                "Foundation layout modifiers can be declared only under a compatible parent",
            ),
            "the actual native parent must fail Foundation's parent protocol",
        )
    }

    @Test
    fun `a deferred transition scale is accepted under a Foundation parent`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            setContent {
                Box {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        visible = { it },
                        enter = scaleIn(),
                    ) { Block() }
                }
            }
            state.defer(true)
            awaitIdle()
            assertEquals(1, root.componentCount)
        }

    @Test
    fun `a scoped transition scale maps input to the painted content`() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var visible by mutableStateOf(false)
            var clicks = 0
            setContent {
                Window(
                    onCloseRequest = {},
                    title = "scoped-scale-hit-test",
                    state = WindowState(size = Dimension(240, 120)),
                ) {
                    Box(SwingModifier.preferredSize(100, 40)) {
                        val transition = updateTransition(visible)
                        transition.AnimatedVisibility(
                            visible = { it },
                            enter = scaleIn(tween(160), initialScale = 0.5f),
                            exit = ExitTransition.None,
                        ) {
                            Button("Go", { clicks++ }, SwingModifier.testTag("scaledButton").preferredSize(100, 40))
                        }
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            mainClock.advanceTimeBy(80.milliseconds)
            val window = onWindowWithTitle("scoped-scale-hit-test").fetch<JFrame>()
            val button =
                onWindowWithTitle(
                    "scoped-scale-hit-test",
                ).onNodeWithTag("scaledButton").fetch<Component>()
            clickAt(window, SwingUtilities.convertPoint(button, Point(50, 20), window))
            assertEquals(1, clicks, "input inside the scaled paint must reach the button")

            clickAt(window, SwingUtilities.convertPoint(button, Point(3, 20), window))
            assertEquals(1, clicks, "input inside only the unscaled bounds must miss")
        }

    @Test
    fun `an explicit deferred identity scale is accepted under a Foundation parent`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            val transform = MutableTransform().apply { update { scale = 1f } }
            setContent {
                Box {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        visible = { it },
                        mutableTransform = transform,
                    ) { Block() }
                }
            }

            state.defer(true)
            awaitIdle()
            assertEquals(1, root.componentCount)
        }

    @Test
    fun `a receiverless scale added during recomposition is rejected`() =
        runComposeSwingTest {
            var enter by mutableStateOf(EnterTransition.None)
            setContent {
                AnimatedVisibility(visible = true, enter = enter, exit = ExitTransition.None) { Block() }
            }

            enter = scaleIn()
            driveOneFrame()
            val failure =
                assertFailsWith<AssertionError> {
                    waitUntil(timeout = 1.milliseconds) { false }
                }
            assertTrue(failure.message.orEmpty().contains("AnimatedVisibility container scale transitions require"))
        }

    @Test
    fun `fade slide and size continue to work without a Foundation parent`() =
        runComposeSwingTest {
            setContent {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(160)) + slideInHorizontally(tween(160)) + expandIn(tween(160)),
                    exit = fadeOut(tween(160)) + shrinkOut(tween(160)),
                ) { Block() }
            }
            assertEquals(1, root.componentCount)
        }

    @Test
    fun `a Foundation parent supports container scale`() =
        runComposeSwingTest {
            setContent {
                Box {
                    AnimatedVisibility(
                        visible = true,
                        enter = scaleIn(tween(160), initialScale = 0.5f),
                        exit = ExitTransition.None,
                    ) {
                        Block()
                    }
                }
            }
            assertEquals(1, root.componentCount)
        }

    @Test
    fun `child scale remains supported under a receiverless container`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = ExitTransition.None) {
                    Box(SwingModifier.animateEnterExit(enter = scaleIn(tween(160), initialScale = 0.5f))) {
                        Block()
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            driveOneFrame()
            assertTrue(animatedContainer().paintedWidth() in 1 until 40)
        }
}

private suspend fun ComposeSwingTest.clickAt(
    frame: JFrame,
    point: Point,
) {
    val screen = Point(point).apply { SwingUtilities.convertPointToScreen(this, frame) }
    for (
    (id, modifiers, button) in
    listOf(
        Triple(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1),
        Triple(MouseEvent.MOUSE_RELEASED, 0, MouseEvent.BUTTON1),
        Triple(MouseEvent.MOUSE_CLICKED, 0, MouseEvent.BUTTON1),
    )
    ) {
        frame.toolkit.systemEventQueue.postEvent(
            MouseEvent(
                frame,
                id,
                System.currentTimeMillis(),
                modifiers,
                point.x,
                point.y,
                screen.x,
                screen.y,
                1,
                false,
                button,
            ),
        )
        awaitIdle()
    }
}
