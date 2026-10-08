package org.jetbrains.compose.swing.animation

import org.jetbrains.compose.swing.foundation.layout.assertPreferencesSettle
import kotlin.test.Test

/** While a transition runs, the size the container requests differs from the size the parent grants. */
class AnimatedVisibilityPreferencesAtWidthTest {
    @Test
    fun `a stock parent's preference read leaves no work behind part-way through an enter that keeps the size`() =
        assertPreferencesSettleAtTheLeafsHeight(
            sizeKeepingEnteringContainers(finished = false, widthFromLeafHeight = false),
            transitionTime = PartWay,
            widthFromLeafHeight =
                sizeKeepingEnteringContainers(finished = false, widthFromLeafHeight = true).atOwnRatio,
        )

    @Test
    fun `a stock parent's preference read leaves no work behind part-way through an enter that resizes`() =
        assertPreferencesSettle(
            sizeChangingEnteringContainers(finished = false, widthFromLeafHeight = false).all,
            transitionTime = PartWay,
            widthFromLeafHeight = sizeChangingEnteringContainers(finished = false, widthFromLeafHeight = true).all,
        )

    @Test
    fun `a stock parent's preference read leaves no work behind part-way through an exit that keeps the size`() {
        assertPreferencesSettleAtTheLeafsHeight(
            sizeKeepingExitingContainers(widthFromLeafHeight = false),
            transitionTime = SlideExitTime,
            widthFromLeafHeight = sizeKeepingExitingContainers(widthFromLeafHeight = true).atOwnRatio,
        )
    }

    @Test
    fun `a stock parent's preference read leaves no work behind part-way through an exit that resizes`() =
        assertPreferencesSettle(
            sizeChangingExitingContainers(widthFromLeafHeight = false).all,
            transitionTime = PartWay,
            widthFromLeafHeight = sizeChangingExitingContainers(widthFromLeafHeight = true).all,
        )

    @Test
    fun `a stock parent's preference read leaves no work behind after an enter`() =
        assertPreferencesSettleAtTheLeafsHeight(
            sizeKeepingEnteringContainers(finished = true, widthFromLeafHeight = false) +
                sizeChangingEnteringContainers(finished = true, widthFromLeafHeight = false),
            widthFromLeafHeight =
                sizeKeepingEnteringContainers(finished = true, widthFromLeafHeight = true).atOwnRatio,
        )
}
