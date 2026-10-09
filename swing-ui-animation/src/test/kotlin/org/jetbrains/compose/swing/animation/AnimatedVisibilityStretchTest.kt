package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.Stretch.CONTENT
import org.jetbrains.compose.swing.animation.Stretch.FRAME_MILLIS
import org.jetbrains.compose.swing.animation.Stretch.FillingContent
import org.jetbrains.compose.swing.animation.Stretch.FillingOwnSize
import org.jetbrains.compose.swing.animation.Stretch.FollowingParents
import org.jetbrains.compose.swing.animation.Stretch.InNorthSlot
import org.jetbrains.compose.swing.animation.Stretch.InViewport
import org.jetbrains.compose.swing.animation.Stretch.NARROW
import org.jetbrains.compose.swing.animation.Stretch.StretchingParents
import org.jetbrains.compose.swing.animation.Stretch.TRANSITION_MILLIS
import org.jetbrains.compose.swing.animation.Stretch.WIDE
import org.jetbrains.compose.swing.animation.Stretch.WideContent
import org.jetbrains.compose.swing.animation.Stretch.atAspect
import org.jetbrains.compose.swing.animation.Stretch.observeEntering
import org.jetbrains.compose.swing.animation.Stretch.resizedSizes
import org.jetbrains.compose.swing.animation.Stretch.underParents
import org.jetbrains.compose.swing.animation.Stretch.visibility
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals

/** How an [AnimatedVisibility] directly under a stock parent lays its content out while it enters. */
class AnimatedVisibilityStretchTest {
    @Test
    fun `without a size change an enter lays the content out in the space a stretching stock parent grants`() {
        val enter = fadeIn(tween(TRANSITION_MILLIS, TRANSITION_MILLIS))
        val observed = underParents(StretchingParents, mapOf("fadeIn" to visibility(enter, WideContent)))
        assertEquals(observed.mapValues { atAspect(NARROW) }, observed.mapValues { (_, it) -> it.whileEntering })
    }

    /**
     * While a size change runs, the content is laid out at its own size, whatever the stretching parent grants, and in
     * the parent's space once the enter ends. Here androidx measures the content with the constraints its parent hands
     * the container, on every frame, so it lays the content out in the stretched space on every frame. Aligning with
     * androidx turns this test red.
     */
    @Test
    fun `a size change lays the content out at its own size until the enter ends under a stretching stock parent`() {
        val observed = underParents(StretchingParents, mapOf("expandIn" to expandingIn()))
        assertEquals(
            observed.mapValues { listOf(Dimension(WIDE, 20), atAspect(NARROW)) },
            observed.mapValues { (_, it) -> it.taken },
            "the content's size at the end of every frame, a repeat once, from the enter to the end of the transition",
        )
        assertEquals(observed.mapValues { atAspect(NARROW) }, observed.mapValues { (_, it) -> it.settled })
    }

    /**
     * Under a stock parent that grants the size answered, the content is laid out at its own size on every frame the
     * change runs, and at the aspect ratio of that width when it ends. Here androidx measures the content with the
     * constraints its parent hands the container, on every frame, so the content holds the ratio at its preferred width
     * on every frame. Aligning with androidx turns this test red.
     */
    @Test
    fun `under a stock parent that grants the size answered the content is at its own size until the change ends`() {
        val observed = underParents(FollowingParents, mapOf("expandIn" to expandingIn()))
        assertEquals(
            observed.mapValues { listOf(Dimension(WIDE, 20), atAspect(WIDE)) },
            observed.mapValues { (_, it) -> it.taken },
            "the content's size at the end of every frame, a repeat once, from the enter to the end of the transition",
        )
        assertEquals(observed.mapValues { atAspect(WIDE) }, observed.mapValues { (_, it) -> it.settled })
    }

    /**
     * Under a north slot, the content is laid out at its own size on every frame the change runs and at the slot's
     * width when it ends. Here androidx measures the content with the constraints its parent hands the container, on
     * every frame, so the content is laid out at the slot's width on every frame. Aligning with androidx turns this
     * test red.
     */
    @Test
    fun `under a north slot the content is at its own size until the change ends, then at the slot's width`() {
        val observed = underParents(mapOf("BorderLayout NORTH" to InNorthSlot), mapOf("expandIn" to expandingIn()))
        assertEquals(
            observed.mapValues { listOf(Dimension(WIDE, 20), atAspect(NARROW)) },
            observed.mapValues { (_, it) -> it.taken },
            "the content's size at the end of every frame, a repeat once, from the enter to the end of the transition",
        )
        assertEquals(observed.mapValues { atAspect(NARROW) }, observed.mapValues { (_, it) -> it.settled })
    }

    /**
     * The size change lasts until the transition ends, so content whose size animation ends before its fade keeps its
     * own size until the fade ends.
     */
    @Test
    fun `a size animation shorter than the fade leaves the content at its own size until the fade ends`() =
        runComposeSwingTest {
            var shown by mutableStateOf(false)
            val taken = mutableListOf<Dimension>()
            val enter = expandIn(tween(TRANSITION_MILLIS / 4)) + fadeIn(tween(2 * TRANSITION_MILLIS))
            val container = visibility(enter, WideContent)
            val parent = FollowingParents.getValue("FlowLayout")
            setContent { parent { modifier -> container(modifier, shown, SwingModifier.testTag(CONTENT)) } }
            mainClock.autoAdvance = false
            shown = true
            repeat(TRANSITION_MILLIS / FRAME_MILLIS) {
                driveOneFrame()
                taken.recordChange(onNodeWithTag(CONTENT).fetch().size)
            }
            assertEquals(listOf(Dimension(WIDE, 20)), taken, "the size animation has ended and the fade has not")

            repeat(2 * TRANSITION_MILLIS / FRAME_MILLIS) {
                driveOneFrame()
                taken.recordChange(onNodeWithTag(CONTENT).fetch().size)
            }
            assertEquals(listOf(Dimension(WIDE, 20), atAspect(WIDE)), taken, "and the fade ending fits the content")
        }

    private fun expandingIn() = visibility(expandIn(tween(TRANSITION_MILLIS, TRANSITION_MILLIS)), WideContent)

    /**
     * Content resized part-way through a change takes its new own size on every frame of a size change, and its aspect
     * ratio at the new width when the change keeps the size. Here androidx measures the content with the constraints
     * its parent hands the container, on every frame, so it holds the ratio at the new width on every frame. Aligning
     * with androidx turns this test red.
     */
    @Test
    fun `under a stock parent that grants the size answered resized content takes its new own size on every frame`() {
        val containers =
            mapOf(
                "expandIn" to { content: TaggedContent -> visibility(expandIn(tween(TRANSITION_MILLIS)), content) },
                "fadeIn" to { content: TaggedContent -> visibility(fadeIn(tween(TRANSITION_MILLIS)), content) },
            )
        val resizes =
            mapOf(
                "growing under FlowLayout" to (FollowingParents.getValue("FlowLayout") to (NARROW to WIDE)),
                "shrinking under BoxLayout Y_AXIS" to
                    (FollowingParents.getValue("BoxLayout Y_AXIS at its preferred size") to (WIDE to NARROW)),
            )
        val observed =
            buildMap {
                for ((resize, parentAndWidths) in resizes) {
                    val (parent, widths) = parentAndWidths
                    for ((kind, container) in containers) {
                        put(
                            "$resize, $kind",
                            resizedSizes(parent, container, widths) to
                                if (kind == "fadeIn") atAspect(widths.second) else Dimension(widths.second, 20),
                        )
                    }
                }
            }
        assertEquals(observed.mapValues { (_, it) -> setOf(it.second) }, observed.mapValues { (_, it) -> it.first })
    }

    /**
     * An enter that keeps the size lays the content out at the width the view settles on. An enter that changes the
     * size lays filling content out at its own size while it runs, which is the text's width and no ratio's height.
     * Here androidx measures the content with the constraints its parent hands the container, on every frame, so it
     * lays the filling content out at the extent. Aligning with androidx turns this test red.
     *
     * A viewport scrolls both ways, so both maxima are released. androidx's scroll modifier releases only the
     * scroll-axis maximum and keeps the cross-axis one (`ScrollingLayoutNode.measure`, Scroll.kt:452-456 under
     * compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation).
     */
    @Test
    fun `as the view a viewport stretches an enter lays the content out at the width the view settles on`() {
        val sizeKeeping =
            mapOf(
                "fadeIn" to fadeIn(tween(TRANSITION_MILLIS, TRANSITION_MILLIS)),
                "expandVertically" to expandVertically(tween(TRANSITION_MILLIS, TRANSITION_MILLIS)),
            )
        val expanding =
            mapOf(
                "expandIn" to expandIn(tween(TRANSITION_MILLIS, TRANSITION_MILLIS)),
                "expandHorizontally" to expandHorizontally(tween(TRANSITION_MILLIS, TRANSITION_MILLIS)),
            )
        // Wider than the viewport, the content keeps its own width; narrower, it fills the viewport's.
        val sizes = mutableMapOf<String, Dimension>()
        val expected = mutableMapOf<String, Dimension>()
        val settled = mutableMapOf<String, Dimension>()
        for ((kind, enter) in sizeKeeping + expanding) {
            val wide = observeEntering(InViewport, visibility(enter, WideContent))
            sizes["wide, $kind"] = wide.whileEntering
            settled["wide, $kind"] = wide.settled
            expected["wide, $kind"] = if (kind == "fadeIn") atAspect(WIDE) else Dimension(WIDE, 20)
        }
        for ((kind, enter) in sizeKeeping + expanding) {
            val filling = observeEntering(InViewport, visibility(enter, FillingContent))
            sizes["filling, $kind"] = filling.whileEntering
            expected["filling, $kind"] =
                if (kind == "fadeIn") atAspect(checkNotNull(filling.viewportExtent).width) else FillingOwnSize
        }
        assertEquals(expected, sizes, "the content's size while entering")
        assertEquals(settled.mapValues { atAspect(WIDE) }, settled, "the content's size settled")
    }
}
