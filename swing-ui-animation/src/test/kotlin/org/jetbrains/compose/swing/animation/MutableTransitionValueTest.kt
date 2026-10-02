package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.DeferredTransitionState
import org.jetbrains.compose.swing.animation.core.ExperimentalDeferredTransitionApi
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.rememberTransition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/** Mutable callback inputs stay isolated from animation geometry. */
class MutableTransitionValueTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("transitionCases")
    fun callbacksReceiveCopiesOfTheMeasuredFullSize(case: TransitionCase) =
        runComposeSwingTest {
            var visible by mutableStateOf(case.startsVisible)
            val received = mutableListOf<Dimension>()
            val (enter, exit) = case.transitions(received)
            setContent {
                if (case.withControl) {
                    AnimatedVisibility(
                        visible,
                        SwingModifier.testTag("control"),
                        enter = expandIn(tween(1600)) { Dimension(it.width / 2, it.height) },
                        exit = ExitTransition.None,
                    ) {
                        Label("copy", SwingModifier.preferredSize(80, 40).opaque(true))
                    }
                }
                AnimatedVisibility(visible, SwingModifier.testTag(CONTAINER), enter = enter, exit = exit) {
                    Label("", SwingModifier.testTag("content").preferredSize(80, 40).opaque(true))
                }
            }
            awaitIdle()
            mainClock.autoAdvance = false
            visible = !case.startsVisible
            driveOneFrame()
            onNodeWithTag("content").assertProperty(case.startBounds, "starts with the callback's returned geometry") {
                bounds
            }
            container().assertProperty(
                Dimension(case.startBounds.width, case.startBounds.height),
                "starts at the full size or the returned initial size",
            ) { size }
            mainClock.advanceTimeBy(500.milliseconds)
            driveOneFrame()
            case.midway(this)
            mainClock.advanceTimeBy(1700.milliseconds)
            awaitIdle()
            case.settled(this)
            assertEquals(
                setOf(Dimension(80, 40)),
                received.toSet(),
                "every call receives the measured full size",
            )
        }

    @Test
    fun anExitInterruptingAnEnterOfAnotherAlignmentReceivesACopyOfTheFullSize() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            val received = mutableListOf<Dimension>()
            val enter =
                expandIn(tween(1600, easing = LinearEasing), Alignment.TopStart) { Dimension(it.width / 2, it.height) }

            fun exit(targetSize: (Dimension) -> Dimension) =
                shrinkOut(
                    tween(1000, easing = LinearEasing),
                    Alignment.BottomEnd,
                    targetSize = targetSize,
                )
            setContent {
                Column {
                    Box {
                        AnimatedVisibility(
                            visible,
                            SwingModifier.testTag("control"),
                            enter = enter,
                            exit = exit { Dimension(40, 20) },
                        ) {
                            Label("", SwingModifier.preferredSize(80, 40).opaque(true))
                        }
                    }
                    Box {
                        AnimatedVisibility(
                            visible,
                            SwingModifier.testTag(CONTAINER),
                            enter = enter,
                            exit =
                                exit {
                                    received += Dimension(it)
                                    it.setSize(3, 4)
                                    Dimension(40, 20)
                                },
                        ) {
                            Label("", SwingModifier.preferredSize(80, 40).opaque(true))
                        }
                    }
                }
            }
            awaitIdle()
            mainClock.autoAdvance = false
            visible = true
            repeat(4) { driveOneFrame() }
            visible = false

            repeat(6) {
                driveOneFrame()
                container().assertProperty(
                    onNodeWithTag("control").fetch().bounds,
                    "mutating the size matches a fresh size while the exit travels",
                ) { bounds }
                container().assertProperty(
                    onNodeWithTag("control").fetch().parent.size,
                    "mutating the size matches a fresh size for the parent's box",
                ) { parent.size }
            }
            assertEquals(setOf(Dimension(80, 40)), received.toSet(), "every call receives the measured full size")
        }

    @OptIn(ExperimentalDeferredTransitionApi::class)
    @ParameterizedTest(name = "{0}")
    @MethodSource("deferredTransformCases")
    fun deferredTransformGeometryIsCopied(
        name: String,
        mutate: TransformScope.(Dimension, Float) -> Unit,
    ) = runComposeSwingTest {
        var fraction by mutableStateOf(0f)
        val state = DeferredTransitionState(false)
        val received = mutableListOf<Dimension>()
        val transform =
            MutableTransform().apply {
                update {
                    received += Dimension(it)
                    mutate(it, fraction)
                }
            }
        setContent {
            DeferredViewport {
                rememberTransition(state).DeferredAnimatedVisibility(
                    modifier = SwingModifier.testTag("deferredVisibility"),
                    visible = { it },
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                    mutableTransform = transform,
                ) {
                    DeferredBlock()
                }
            }
        }
        state.defer(true)
        awaitIdle()
        assertEquals(0, paintedDeferredContentLeft(), "$name: content starts at the origin")
        fraction = 0.5f
        awaitIdle()
        assertEquals(20, paintedDeferredContentLeft(), "$name: content moves by half its measured width")
        assertEquals(Dimension(40, 20), content().size, "$name: content keeps its measured size")
        assertEquals(
            setOf(Dimension(40, 20)),
            received.toSet(),
            "every layout receives the measured full size",
        )
    }

    class TransitionCase(
        val startsVisible: Boolean,
        val withControl: Boolean = false,
        val transitions: (MutableList<Dimension>) -> Pair<EnterTransition, ExitTransition>,
        val startBounds: Rectangle,
        val midway: ComposeSwingTest.() -> Unit,
        val settled: ComposeSwingTest.() -> Unit,
    )

    companion object {
        private val cases: Map<String, TransitionCase> =
            mapOf(
                "expandIn" to
                    TransitionCase(
                        startsVisible = false,
                        withControl = true,
                        transitions = { received ->
                            expandIn(tween(1600)) {
                                received += Dimension(it)
                                it.apply { width /= 2 }
                            } to ExitTransition.None
                        },
                        startBounds = Rectangle(0, 0, 40, 40),
                        midway = {
                            container().assertProperty(
                                onNodeWithTag("control").fetch().preferredSize,
                                "mutating the initial size matches a fresh size while traveling",
                            ) { preferredSize }
                        },
                        settled = {
                            container().assertProperty(
                                Dimension(80, 40),
                                "expandIn settles at measured full size",
                            ) { size }
                            container().assertProperty(
                                onNodeWithTag("control").fetch().preferredSize,
                                "both callbacks settle at measured full size",
                            ) { preferredSize }
                        },
                    ),
                "shrinkOut" to
                    TransitionCase(
                        startsVisible = true,
                        transitions = { received ->
                            EnterTransition.None to
                                shrinkOut(tween(1000, easing = LinearEasing), clip = false) {
                                    received += Dimension(it)
                                    it.setSize(3, 4)
                                    Dimension(40, 20)
                                }
                        },
                        startBounds = Rectangle(0, 0, 80, 40),
                        midway = {
                            container().assertProperty(
                                Dimension(59, 30),
                                "the exit shrinks toward the returned size",
                            ) { size }
                            onNodeWithTag("content").assertProperty(
                                Rectangle(0, 0, 59, 30),
                                "the content travels toward the callback's returned geometry",
                            ) { bounds }
                        },
                        settled = { onNodeWithTag(CONTAINER).assertDoesNotExist() },
                    ),
                "slideIn" to
                    TransitionCase(
                        startsVisible = false,
                        transitions = { received ->
                            slideIn(tween(1000, easing = LinearEasing)) {
                                received += Dimension(it)
                                it.setSize(3, 4)
                                Point(-20, 10)
                            } to ExitTransition.None
                        },
                        startBounds = Rectangle(-20, 10, 80, 40),
                        midway = {
                            onNodeWithTag("content").assertProperty(
                                Rectangle(-10, 5, 80, 40),
                                "the content travels toward the callback's returned geometry",
                            ) { bounds }
                        },
                        settled = {
                            onNodeWithTag("content").assertProperty(
                                Rectangle(0, 0, 80, 40),
                                "slideIn settles in place",
                            ) { bounds }
                            container().assertProperty(
                                Dimension(80, 40),
                                "slideIn keeps the measured full size",
                            ) { size }
                        },
                    ),
                "slideOut" to
                    TransitionCase(
                        startsVisible = true,
                        transitions = { received ->
                            EnterTransition.None to
                                slideOut(tween(1000, easing = LinearEasing)) {
                                    received += Dimension(it)
                                    it.setSize(3, 4)
                                    Point(-20, 10)
                                }
                        },
                        startBounds = Rectangle(0, 0, 80, 40),
                        midway = {
                            onNodeWithTag("content").assertProperty(
                                Rectangle(-10, 5, 80, 40),
                                "the content travels toward the callback's returned geometry",
                            ) { bounds }
                        },
                        settled = { onNodeWithTag(CONTAINER).assertDoesNotExist() },
                    ),
            )

        private fun ComposeSwingTest.container() = onNodeWithTag(CONTAINER)

        @JvmStatic
        fun transitionCases(): List<Arguments> = cases.map { (name, case) -> Arguments.of(Named.of(name, case)) }

        @JvmStatic
        fun deferredTransformCases(): List<Arguments> =
            mapOf<String, TransformScope.(Dimension, Float) -> Unit>(
                "full size is a copy" to { size, fraction ->
                    offset = Point((size.width * fraction).toInt(), 0)
                    size.setSize(3, 4)
                },
                "assigning an offset copies it" to { size, fraction ->
                    val assigned = Point((size.width * fraction).toInt(), 0)
                    offset = assigned
                    assigned.setLocation(99, 99)
                },
                "reading an offset returns a copy" to { size, fraction ->
                    offset = Point((size.width * fraction).toInt(), 0)
                    offset.setLocation(77, 77)
                },
            ).map { (name, mutate) -> Arguments.of(name, mutate) }
    }
}

private const val CONTAINER = "container"
