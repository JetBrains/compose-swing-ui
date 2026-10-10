package org.jetbrains.compose.swing.animation

import org.jetbrains.compose.swing.animation.Stretch.FollowingParents
import org.jetbrains.compose.swing.animation.Stretch.InNorthSlot
import org.jetbrains.compose.swing.animation.Stretch.NARROW
import org.jetbrains.compose.swing.animation.Stretch.StretchingParents
import org.jetbrains.compose.swing.animation.Stretch.TRANSITION_MILLIS
import org.jetbrains.compose.swing.animation.Stretch.WIDE
import org.jetbrains.compose.swing.animation.Stretch.atAspect
import org.jetbrains.compose.swing.animation.Stretch.replacing
import org.jetbrains.compose.swing.animation.Stretch.resizedSizes
import org.jetbrains.compose.swing.animation.Stretch.underParents
import org.jetbrains.compose.swing.animation.core.tween
import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals

/** How an [AnimatedContent] directly under a stock parent lays its content out while its content changes. */
class AnimatedContentStretchTest {
    /**
     * While a size change runs, the replaced content is laid out at its own size, whatever the stretching parent
     * grants, and in the parent's space once the change ends. Here androidx's `AnimatedContentMeasurePolicy.measure`
     * (AnimatedContent.kt:1305-1330 under compose/animation/animation/src/commonMain/kotlin/androidx/compose/animation)
     * measures every content with the constraints its parent hands the container, so it lays the content out in the
     * stretched space on every frame. Aligning with androidx turns this test red.
     */
    @Test
    fun `a content change lays the content out at its own size until it ends under a stretching stock parent`() {
        val observed = underParents(StretchingParents, contentChanges())
        assertEquals(
            observed.mapValues { listOf(Dimension(WIDE, 20), atAspect(NARROW)) },
            observed.mapValues { (_, it) -> it.taken },
            "the content's size at the end of every frame, a repeat once, from the enter to the end of the transition",
        )
    }

    /**
     * Under a stock parent that grants the size answered, the content is laid out at its own size on every frame the
     * change runs, and at the aspect ratio of that width when it ends. Here androidx's
     * `AnimatedContentMeasurePolicy.measure` (AnimatedContent.kt:1305-1330 under
     * compose/animation/animation/src/commonMain/kotlin/androidx/compose/animation) measures every content with the
     * constraints its parent hands the container, so the content holds the ratio at its preferred width on every frame.
     * Aligning with androidx turns this test red.
     */
    @Test
    fun `under a stock parent that grants the size answered the content is at its own size until the change ends`() {
        val observed = underParents(FollowingParents, contentChanges())
        assertEquals(
            observed.mapValues { listOf(Dimension(WIDE, 20), atAspect(WIDE)) },
            observed.mapValues { (_, it) -> it.taken },
            "the content's size at the end of every frame, a repeat once, from the enter to the end of the transition",
        )
        assertEquals(observed.mapValues { atAspect(WIDE) }, observed.mapValues { (_, it) -> it.settled })
    }

    /**
     * Under a north slot, the content is laid out at its own size on every frame the change runs and at the slot's
     * width when it ends. Here androidx's `AnimatedContentMeasurePolicy.measure` (AnimatedContent.kt:1305-1330 under
     * compose/animation/animation/src/commonMain/kotlin/androidx/compose/animation) measures every content with the
     * constraints its parent hands the container, so the content is laid out at the slot's width on every frame.
     * Aligning with androidx turns this test red.
     */
    @Test
    fun `under a north slot the content is at its own size until the change ends, then at the slot's width`() {
        val observed = underParents(mapOf("BorderLayout NORTH" to InNorthSlot), contentChanges())
        assertEquals(
            observed.mapValues { listOf(Dimension(WIDE, 20), atAspect(NARROW)) },
            observed.mapValues { (_, it) -> it.taken },
            "the content's size at the end of every frame, a repeat once, from the enter to the end of the transition",
        )
        assertEquals(observed.mapValues { atAspect(NARROW) }, observed.mapValues { (_, it) -> it.settled })
    }

    /**
     * Content resized part-way through a change takes its new own size on every frame of a size change. Here androidx's
     * `AnimatedContentMeasurePolicy.measure` (AnimatedContent.kt:1305-1330 under
     * compose/animation/animation/src/commonMain/kotlin/androidx/compose/animation) measures every content with the
     * constraints its parent hands the container, so it holds the ratio at the new width on every frame. Aligning with
     * androidx turns this test red.
     */
    @Test
    fun `under a stock parent that grants the size answered resized content takes its new own size on every frame`() {
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
                    for ((kind, transform) in transforms()) {
                        put(
                            "$resize, $kind",
                            resizedSizes(parent, { content -> replacing(transform, content) }, widths) to
                                Dimension(widths.second, 20),
                        )
                    }
                }
            }
        assertEquals(observed.mapValues { (_, it) -> setOf(it.second) }, observed.mapValues { (_, it) -> it.first })
    }

    private fun transforms(): Map<String, ContentTransform> =
        mapOf(
            "fade" to (fadeIn(tween(TRANSITION_MILLIS)) togetherWith fadeOut(tween(TRANSITION_MILLIS))),
            "expand and shrink" to (
                expandIn(
                    tween(TRANSITION_MILLIS),
                ) togetherWith shrinkOut(tween(TRANSITION_MILLIS))
            ),
        )

    private fun contentChanges(): Map<String, EnteringContainer> = transforms().mapValues { (_, it) -> replacing(it) }
}
