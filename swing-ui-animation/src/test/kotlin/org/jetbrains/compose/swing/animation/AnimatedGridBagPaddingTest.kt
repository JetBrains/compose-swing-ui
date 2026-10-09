package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.GridBagPadding.OwnSize
import org.jetbrains.compose.swing.animation.GridBagPadding.Paddings
import org.jetbrains.compose.swing.animation.GridBagPadding.Run
import org.jetbrains.compose.swing.animation.GridBagPadding.TRANSITION_MILLIS
import org.jetbrains.compose.swing.animation.GridBagPadding.WrappingContent
import org.jetbrains.compose.swing.animation.GridBagPadding.observe
import org.jetbrains.compose.swing.animation.GridBagPadding.padded
import org.jetbrains.compose.swing.animation.GridBagPadding.visibility
import org.jetbrains.compose.swing.animation.core.tween
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An [AnimatedVisibility] in a `GridBagLayout` cell with internal padding, which grants the container the size it
 * answers plus the padding. While a size change runs, that grant is short of the content by up to its own size.
 */
class AnimatedGridBagPaddingTest {
    @Test
    fun `an expand in a cell with internal padding lays the content out at its own size on every frame`() {
        val enters =
            mapOf(
                "expandIn" to expandIn(tween(TRANSITION_MILLIS)),
                "expandHorizontally" to expandHorizontally(tween(TRANSITION_MILLIS)),
                "expandVertically" to expandVertically(tween(TRANSITION_MILLIS)),
            )
        val observed =
            buildMap {
                for ((padding, pad) in Paddings) {
                    for ((kind, enter) in enters) put("$padding, $kind", observe(pad, visibility(enter)))
                }
            }
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
        val wrapping = @Composable { WrappingContent() }
        val changes =
            mapOf(
                "expandIn" to visibility(expandIn(tween(TRANSITION_MILLIS)), wrapping),
                "expandHorizontally" to visibility(expandHorizontally(tween(TRANSITION_MILLIS)), wrapping),
                "expandVertically" to visibility(expandVertically(tween(TRANSITION_MILLIS)), wrapping),
            )
        val observed =
            buildMap {
                for ((padding, pad) in Paddings) {
                    for ((kind, change) in changes) put("$padding, $kind", observe(pad, change))
                }
            }
        assertEquals(observed.mapValues { listOf(Run.Absent, Run.Own) }, observed.mapValues { (_, it) -> it.runs })
        assertEquals(
            observed.mapValues { (name, _) -> padded(name) to true },
            observed.mapValues { (_, it) -> it.containerEnd to it.containerGrows },
            "the container's size once entered, and whether it never shrank on the way",
        )
    }

    @Test
    fun `a shrinkOut of wrapping content in a cell with internal padding keeps its own size until it is gone`() {
        val exit = shrinkOut(tween(TRANSITION_MILLIS))
        val exiting: PaddedContainer = { modifier, shown ->
            AnimatedVisibility(shown, modifier, enter = EnterTransition.None, exit = exit) { WrappingContent() }
        }
        val observed = Paddings.mapValues { (_, pad) -> observe(pad, exiting, shown = false) }
        assertEquals(observed.mapValues { listOf(Run.Own, Run.Absent) }, observed.mapValues { (_, it) -> it.runs })
    }
}
