package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.ConstrainedPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.test.assertEquals

/** A Foundation container repaints an entering child's changed area grown by the child's outsets only. */
class EnterExitRepaintWithinOutsetsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("enterCases")
    fun aRepaintOverAShadowedChildUnderAnEnterGrowsOnlyByTheOutsets(
        scoped: Boolean,
        enter: EnterTransition,
    ) = runComposeSwingTest {
        var visible by mutableStateOf(false)
        setContent {
            Box(modifier = SwingModifier.preferredSize(260, 120).testTag("host")) {
                if (scoped) {
                    AnimatedVisibility(visible = visible, enter = enter, exit = ExitTransition.None) { ShadowedLeaf() }
                } else {
                    UnscopedAnimatedVisibility(visible, enter) { ShadowedLeaf() }
                }
            }
        }
        awaitIdle()
        mainClock.autoAdvance = false
        visible = !visible
        repeat(4) { driveOneFrame() }

        val host = onNodeWithTag("host").fetch<ConstrainedPanel>()
        val leaf = onNodeWithTag("leaf").fetch<JComponent>()
        val dirty = SwingUtilities.convertRectangle(leaf, Rectangle(10, 20, 1, 1), host)
        var grown: Rectangle? = null
        host.decoration.paintImmediately(host, dirty.x, dirty.y, dirty.width, dirty.height) { x, y, width, height ->
            grown = Rectangle(x, y, width, height)
        }
        val area = checkNotNull(grown)
        assertEquals(
            Rectangle(10, 20, 2, 1),
            area,
            "the repaint grows by the one pixel the shadow spreads right",
        )
    }

    private companion object {
        @JvmStatic
        fun enterCases(): List<Arguments> =
            listOf(
                Arguments.of(Named.of("a veil under a Foundation parent", true), unveilIn(tween(10_000))),
                Arguments.of(Named.of("a fade without a Foundation parent", false), fadeIn(tween(10_000))),
            )
    }
}

// No ConstrainedScope receiver, so the call resolves to the unscoped overload.
@Composable
private fun UnscopedAnimatedVisibility(
    visible: Boolean,
    enter: EnterTransition,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) = AnimatedVisibility(visible = visible, enter = enter, exit = ExitTransition.None, content = content)

@Composable
private fun ShadowedLeaf() {
    Box(modifier = SwingModifier.preferredSize(240, 100)) {
        Box(modifier = SwingModifier.preferredSize(200, 60).testTag("card").shadow(0, Color.BLACK, offsetX = 1)) {
            Canvas(modifier = SwingModifier.preferredSize(200, 60).testTag("leaf")) {
                drawRect(Color.RED, x = 10f, y = 20f, width = 1f, height = 1f)
            }
        }
    }
}
