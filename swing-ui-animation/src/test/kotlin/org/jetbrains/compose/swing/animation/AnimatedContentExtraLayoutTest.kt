package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertTrue

/** Layouts in which nothing changed neither move nor finish a size-transformed content replacement; only frames do. */
class AnimatedContentExtraLayoutTest {
    @ParameterizedTest(name = "{0}, {1} unchanged layouts")
    @MethodSource(UNCHANGED_LAYOUT_CASES)
    fun `unchanged layouts cannot change a size transform or the replacement it runs`(
        resizes: FrameResizes,
        layouts: Int,
    ) = assertUnchangedLayoutsChangeNothing(resizes, layouts, ::replacementScene)
}

private fun replacementScene(): LayoutScene<Boolean> =
    LayoutScene(
        content = { changed -> TweenContent(changed) },
        observe = {
            LayoutObservation(
                onNodeWithTag("animation").fetch().size,
                onAllNodesWithTag("old").fetchAll().isEmpty(),
            )
        },
        assertSettled = { assertTrue(it, "the old content has left") },
    )

@Composable
private fun TweenContent(changed: Boolean) {
    AnimatedContent(changed, SwingModifier.testTag("animation"), transitionSpec = {
        (fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))) using
            SizeTransform { _, _ -> tween(1000, easing = LinearEasing) }
    }) {
        Label(
            "",
            modifier = SwingModifier.testTag(if (it) "body" else "old").preferredSize(if (it) 400 else 80, 40),
        )
    }
}
