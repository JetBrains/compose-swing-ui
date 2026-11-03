// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.click
import org.jetbrains.compose.swing.test.interaction.performMouseInput
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import java.awt.event.MouseEvent
import javax.swing.JComponent
import kotlin.test.Test

/** Input remains correct when a container replaces its composed children. */
class PlacementHitTestLifecycleTest {
    /**
     * A panel emptied by disposing its composition reads presses through the layer of a child the next composition
     * turns, so they reach a component inside that child.
     */
    @Test
    fun aPanelEmptiedByItsCompositionReachesAChildTurnedAfter() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val window = onWindowWithTitle(WINDOW_TITLE)
            val stageInput = window.onNodeWithTag(STAGE_TAG)
            val onPressed = mockk<(MouseEvent) -> Unit>(relaxed = true)
            var mounted by mutableStateOf(false)
            setWindowContent {
                MountedStage(mounted) {
                    Box(
                        modifier = SwingModifier.testTag("layered").size(120, 30).placementLayer { rotationZ = 90f },
                    ) {
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag("inner")
                                    .size(120, 30)
                                    .mouseListener(onMousePressed = onPressed),
                        ) {}
                    }
                }
            }
            repeat(2) { round ->
                mounted = true
                awaitIdle()
                val turn = turn(120, 30, rotation = 90f)

                // Turned, the box paints this point of its canvas 35 above its unturned box.
                stageInput.performMouseInput {
                    click(
                        window.onNodeWithTag("layered").fetch<JComponent>().paintedAt(
                            stageInput.fetch(),
                            window.onNodeWithTag("inner").fetch<JComponent>(),
                            10.0,
                            15.0,
                            turn,
                        ),
                    )
                }
                verify(exactly = round + 1) { onPressed(any()) }
                mounted = false
                awaitIdle()
            }
        }
}

/** A stage that owns a nested composition of [content] while [mounted], and disposes it when not. */
@Composable
private fun MountedStage(
    mounted: Boolean,
    content: @Composable ConstrainedScope.() -> Unit,
) {
    val parent = rememberCompositionContext()
    val latest = rememberUpdatedState(content)
    SwingNode(
        factory = { MountingStage() },
        modifier = SwingModifier.testTag(STAGE_TAG).styleable { preferredSize(300, 300) },
        update = { set(mounted) { if (it) mountUnder(parent) { latest.value(this) } else unmount() } },
        onRelease = { unmount() },
    )
}

private class MountingStage : ConstrainedPanel(maybeCachedBoxMeasurePolicy(Alignment.Center, false)) {
    private var mount: DisposableHandle? = null

    fun mountUnder(
        parent: CompositionContext,
        content: @Composable ConstrainedScope.() -> Unit,
    ) {
        mount?.dispose()
        mount = setContent(parent, content)
    }

    fun unmount() {
        mount?.dispose()
        mount = null
    }
}
