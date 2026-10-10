package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.GridBagPadding.CONTENT
import org.jetbrains.compose.swing.animation.GridBagPadding.OwnSize
import org.jetbrains.compose.swing.animation.GridBagPadding.Paddings
import org.jetbrains.compose.swing.animation.GridBagPadding.Run
import org.jetbrains.compose.swing.animation.GridBagPadding.TRANSITION_MILLIS
import org.jetbrains.compose.swing.animation.GridBagPadding.WrappingContent
import org.jetbrains.compose.swing.animation.GridBagPadding.observe
import org.jetbrains.compose.swing.animation.GridBagPadding.padded
import org.jetbrains.compose.swing.animation.GridBagPadding.replacing
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An [AnimatedContent] in a `GridBagLayout` cell with internal padding, which grants the container the size it
 * answers plus the padding. While a size change runs, that grant is short of the content by up to its own size.
 */
class AnimatedContentGridBagPaddingTest {
    @Test
    fun `a content change in a cell with internal padding lays the new content out at its own size on every frame`() {
        val transform = expandIn(tween(TRANSITION_MILLIS)) togetherWith shrinkOut(tween(TRANSITION_MILLIS))
        val observed = Paddings.mapValues { (_, pad) -> observe(pad, replacing(transform)) }
        assertEquals(observed.mapValues { setOf(OwnSize) }, observed.mapValues { (_, it) -> it.shownSizes })
        assertEquals(
            observed.mapValues { (name, _) -> padded(name) to true },
            observed.mapValues { (_, it) -> it.containerEnd to it.containerGrows },
            "the container's size once entered, and whether it never shrank on the way",
        )
    }

    /**
     * Wrapping content is released from the first measurement of the size change, so it holds its own size on every
     * frame it shows, whatever the cell's padding grants. It is never laid out short of its own size on the way.
     */
    @Test
    fun `wrapping content in a cell with internal padding holds its own size on every frame`() {
        val change = expandIn(tween(TRANSITION_MILLIS)) togetherWith shrinkOut(tween(TRANSITION_MILLIS))
        val observed = Paddings.mapValues { (_, pad) -> observe(pad, replacing(change) { WrappingContent() }) }
        assertEquals(observed.mapValues { listOf(Run.Absent, Run.Own) }, observed.mapValues { (_, it) -> it.runs })
        assertEquals(
            observed.mapValues { (name, _) -> padded(name) to true },
            observed.mapValues { (_, it) -> it.containerEnd to it.containerGrows },
            "the container's size once entered, and whether it never shrank on the way",
        )
    }

    @Test
    fun `a content change back from empty content in a padded cell lays the content out at its own size`() {
        // A fast size change, so the first grant after the empty content zeroed the cell is above the content's
        // minimum.
        val transform = expandIn(tween(TRANSITION_MILLIS)) togetherWith shrinkOut(tween(TRANSITION_MILLIS))
        val sizeTransform = SizeTransform { _, _ -> tween(48) }
        val observed =
            Paddings.mapValues { (_, pad) ->
                val emptied: PaddedContainer = { modifier, shown ->
                    AnimatedContent(shown, modifier, transitionSpec = { transform using sizeTransform }) {
                        if (it) WrappingContent()
                    }
                }
                observe(pad, emptied, roundTrip = true)
            }
        assertEquals(observed.mapValues { listOf(Run.Absent, Run.Own) }, observed.mapValues { (_, it) -> it.runs })
    }

    @Test
    fun `a content change interrupted in a cell with internal padding lays the last target out at its own size`() {
        val sizes = mutableListOf<Dimension>()
        lateinit var own: Dimension
        val transform = fadeIn(tween(TRANSITION_MILLIS)) togetherWith fadeOut(tween(TRANSITION_MILLIS))
        runComposeSwingTest {
            var state by mutableIntStateOf(0)
            setContent {
                Panel(PanelLayout.Flow()) {
                    Panel(PanelLayout.GridBag, modifier = SwingModifier.preferredSize(400, 300)) {
                        AnimatedContent(
                            state,
                            SwingModifier.item(ipadx = Paddings.getValue("ipadx").width),
                            transitionSpec = { transform },
                        ) { shown ->
                            val modifier = SwingModifier.testTag("$CONTENT$shown").preferredSize(100 * (shown + 1), 40)
                            Label("$shown", modifier = modifier)
                        }
                    }
                }
            }
            mainClock.autoAdvance = false
            state = 1
            // Interrupt the change to the second content part of the way through.
            repeat(6) {
                mainClock.advanceTimeByFrame()
                awaitIdle()
            }
            state = 2
            repeat(TRANSITION_MILLIS / 16 + 6) {
                mainClock.advanceTimeByFrame()
                awaitIdle()
                sizes += onAllNodesWithTag("${CONTENT}2").fetchAll<JComponent>().single().size
            }
            own = onAllNodesWithTag("${CONTENT}2").fetchAll<JComponent>().single().preferredSize
        }
        assertEquals(List(sizes.size) { own }, sizes)
    }
}
