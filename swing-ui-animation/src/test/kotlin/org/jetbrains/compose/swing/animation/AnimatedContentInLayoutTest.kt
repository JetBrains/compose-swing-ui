package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Container
import java.awt.GridBagConstraints
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An animated container under the three parents that hand a child the same bounds whatever preferred
 * size it declares: a `BorderLayout` center region, a weighted row child, and a grid-bag cell filled
 * along both axes. A size transition there animates a box its parent never acts on, which is the case
 * where nothing else would carry the frame to the screen.
 */
class AnimatedContentInLayoutTest {
    @Test
    fun `a transition runs to completion under a parent that ignores the size the container asks for`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(REGION_WIDTH, REGION_HEIGHT)) {
                    AnimatedContent(targetState = state, modifier = SwingModifier.center()) { Body(state = it) }
                }
                Row(modifier = SwingModifier.preferredSize(REGION_WIDTH, REGION_HEIGHT)) {
                    AnimatedContent(targetState = state, modifier = SwingModifier.weight(1f)) { Body(state = it) }
                }
                Panel(PanelLayout.GridBag, modifier = SwingModifier.preferredSize(REGION_WIDTH, REGION_HEIGHT)) {
                    AnimatedContent(
                        targetState = state,
                        modifier = SwingModifier.item(weightx = 1.0, weighty = 1.0, fill = GridBagConstraints.BOTH),
                    ) {
                        Body(state = it)
                    }
                }
            }
            val regions = PARENTS.indices.map { containerIn(it).bounds }

            state = "b"
            // The frame the size animation aims at comes from a layout pass, so a container that read its
            // own animated size back would keep producing frames and this would run to the frame ceiling
            // and fail rather than return.
            awaitIdle()

            for ((index, parent) in PARENTS.withIndex()) {
                val container = containerIn(index)
                assertEquals(1, container.componentCount, "$parent: the content that finished exiting stands still")
                assertEquals("b", container.contentText(0), "$parent: the container did not settle on the target")
                assertEquals(regions[index], container.bounds, "$parent: the parent reshaped its region after all")
            }
        }

    /** The animated container standing in the parent at [index], which is that parent's one child. */
    private fun ComposeSwingTest.containerIn(index: Int): Container =
        (root.getComponent(index) as Container).getComponent(0) as Container
}

/** Content whose size changes with the state, so the container's own size animates between the two. */
@Composable
private fun Body(state: String) =
    Label(
        text = state,
        modifier = SwingModifier.preferredSize(width = if (state == "a") 40 else 160, height = 20),
    )

/** The parents under test, named so a failure says which one it is. */
private val PARENTS = listOf("a border layout's center region", "a weighted row child", "a filled grid-bag cell")

private const val REGION_WIDTH = 240
private const val REGION_HEIGHT = 80
