package org.jetbrains.compose.swing.animation

import org.jetbrains.compose.swing.foundation.layout.assertPreferencesSettle
import kotlin.test.Test

class AnimateContentSizePreferencesAtWidthTest {
    @Test
    fun `a stock parent's preference read leaves no work behind while the size animates`() =
        assertPreferencesSettle(
            SizeAnimatingContainers,
            transitionTime = PartWay,
            widthFromLeafHeight = RatioSizeAnimatingContainers,
        )

    @Test
    fun `a stock parent's preference read leaves no work behind after the size animated`() =
        assertPreferencesSettle(SizeAnimatingContainers, widthFromLeafHeight = RatioSizeAnimatingContainers)
}
