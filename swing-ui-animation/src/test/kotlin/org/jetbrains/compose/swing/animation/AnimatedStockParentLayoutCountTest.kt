package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Dimension
import kotlin.test.assertTrue

/**
 * A container hosted directly in a stock `FlowLayout` panel: a layout that runs without a frame changes no history.
 */
class AnimatedStockParentLayoutCountTest {
    @ParameterizedTest(name = "{0}, {1} unchanged layouts")
    @MethodSource(UNCHANGED_LAYOUT_CASES)
    fun `unchanged layouts cannot change an expand of a container in a stock flow panel`(
        resizes: FrameResizes,
        layouts: Int,
    ) = assertUnchangedLayoutsChangeNothing(resizes, layouts, ::expandingVisibilityScene)

    @ParameterizedTest(name = "{0}, {1} unchanged layouts")
    @MethodSource(UNCHANGED_LAYOUT_CASES)
    fun `unchanged layouts cannot change a size transform of a container in a stock flow panel`(
        resizes: FrameResizes,
        layouts: Int,
    ) = assertUnchangedLayoutsChangeNothing(resizes, layouts, ::sizeTransformScene)
}

private fun expandingVisibilityScene(): LayoutScene<Boolean> {
    val state = MutableTransitionState(false)
    return LayoutScene(
        content = { changed ->
            state.targetState = changed
            Panel(PanelLayout.Flow()) {
                FlowVisibility(state, expandIn(tween(1000, easing = LinearEasing)), ExitTransition.None)
            }
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

private fun sizeTransformScene(): LayoutScene<Boolean> =
    LayoutScene(
        content = { changed -> Panel(PanelLayout.Flow()) { FlowTween(changed) } },
        observe = {
            LayoutObservation(
                onNodeWithTag("animation").fetch().size,
                onAllNodesWithTag("old").fetchAll().isEmpty(),
            )
        },
        assertSettled = { assertTrue(it, "the old content has left") },
    )

@Composable
private fun FlowVisibility(
    state: MutableTransitionState<Boolean>,
    enter: EnterTransition,
    exit: ExitTransition,
) {
    AnimatedVisibility(state, SwingModifier.testTag("animation"), enter, exit) {
        Label("", modifier = SwingModifier.testTag("body").preferredSize(80, 40))
    }
}

@Composable
private fun FlowTween(changed: Boolean) {
    AnimatedContent(changed, SwingModifier.testTag("animation"), transitionSpec = {
        (fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))) using
            SizeTransform(sizeAnimationSpec = { _, _ -> tween(1000, easing = LinearEasing) })
    }) {
        Label(
            "",
            modifier =
                SwingModifier
                    .testTag(if (it) "body" else "old")
                    .preferredSize(if (it) 400 else 80, 40),
        )
    }
}
