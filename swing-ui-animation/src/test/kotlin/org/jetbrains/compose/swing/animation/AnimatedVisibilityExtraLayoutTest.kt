package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Dimension
import javax.swing.JLabel
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** Layouts in which nothing changed neither move nor finish an expand, a shrink or a slide; only frames do. */
class AnimatedVisibilityExtraLayoutTest {
    @ParameterizedTest(name = "{0}, {1} unchanged layouts")
    @MethodSource(UNCHANGED_LAYOUT_CASES)
    fun `unchanged layouts cannot change an expanding enter or its completion`(
        resizes: FrameResizes,
        layouts: Int,
    ) = assertUnchangedLayoutsChangeNothing(resizes, layouts) {
        visibilityScene(visibleBeforeChange = false, expandIn(tween(1000, easing = LinearEasing)), ExitTransition.None)
    }

    @ParameterizedTest(name = "{0}, {1} unchanged layouts")
    @MethodSource(UNCHANGED_LAYOUT_CASES)
    fun `unchanged layouts cannot change an expanding and sliding enter or its completion`(
        resizes: FrameResizes,
        layouts: Int,
    ) = assertUnchangedLayoutsChangeNothing(resizes, layouts) {
        visibilityScene(
            visibleBeforeChange = false,
            expandIn(tween(1000, easing = LinearEasing)) + slideInVertically(tween(1000, easing = LinearEasing)),
            ExitTransition.None,
        )
    }

    @ParameterizedTest(name = "{0} layouts before the first frame")
    @ValueSource(ints = [1, 5, 12])
    fun `extra layouts before an exit frame preserve its declared duration`(layouts: Int) =
        runComposeSwingTest {
            mainClock.autoAdvance = false
            val state = MutableTransitionState(true).apply { targetState = false }
            setContent {
                FrameVisibility(state, EnterTransition.None, slideOutVertically(tween(1000, easing = LinearEasing)))
            }
            val body = onNodeWithTag("body").fetch<JLabel>()
            repeat(layouts) { layOutAgain(body) }
            driveOneFrame()
            val firstFrame = mainClock.currentTime
            advanceOneFrameBy(680.milliseconds)
            onAllNodesWithTag("animation").assertCountEquals(1)
            assertEndsAt(
                firstFrame + 1000.milliseconds,
                stillRunning = { onAllNodesWithTag("animation").assertCountEquals(1) },
                ended = { onNodeWithTag("animation").assertDoesNotExist() },
            )
        }
}

/** A visibility whose content is shown when the scene's flag is set; it starts shown when [visibleBeforeChange]. */
private fun visibilityScene(
    visibleBeforeChange: Boolean,
    enter: EnterTransition,
    exit: ExitTransition,
): LayoutScene<Boolean> {
    val state = MutableTransitionState(visibleBeforeChange)
    return LayoutScene(
        content = { changed ->
            state.targetState = if (visibleBeforeChange) !changed else changed
            FrameVisibility(state, enter, exit)
        },
        observe = {
            LayoutObservation(
                onAllNodesWithTag("animation").fetchAll().firstOrNull()?.size ?: Dimension(0, 0),
                state.isIdle,
            )
        },
        assertSettled = { assertTrue(it, "the transition has completed") },
    )
}

@Composable
private fun FrameVisibility(
    state: MutableTransitionState<Boolean>,
    enter: EnterTransition,
    exit: ExitTransition,
) {
    AnimatedVisibility(state, SwingModifier.testTag("animation"), enter, exit) {
        Label("", modifier = SwingModifier.testTag("body").preferredSize(80, 40))
    }
}
