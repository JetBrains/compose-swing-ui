// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
