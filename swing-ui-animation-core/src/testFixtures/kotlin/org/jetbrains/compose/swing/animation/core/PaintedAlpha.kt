// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.core

import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color

/** Returns the alpha of the rendered pixel at ([x], [y]) as a fraction from `0f` to `1f`. */
fun SwingNodeInteraction<*>.paintedAlpha(
    x: Int = 2,
    y: Int = 2,
): Float {
    val maxChannel = 255f
    return Color(captureToImage().getRGB(x, y), true).alpha / maxChannel
}
