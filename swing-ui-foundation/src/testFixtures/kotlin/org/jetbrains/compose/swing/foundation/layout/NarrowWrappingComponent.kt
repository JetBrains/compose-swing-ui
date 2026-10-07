package org.jetbrains.compose.swing.foundation.layout

import java.awt.Dimension
import javax.swing.JComponent

private const val NARROW_PREFERRED_SIZE = 100

/**
 * A widget that prefers a square of [NARROW_PREFERRED_SIZE] and lays out that area at the width it holds, so its height
 * follows its width as a wrapping text's does.
 */
public class NarrowWrappingComponent : JComponent() {
    override fun getPreferredSize(): Dimension =
        Dimension(
            NARROW_PREFERRED_SIZE,
            NARROW_PREFERRED_SIZE * NARROW_PREFERRED_SIZE / (if (width > 0) width else NARROW_PREFERRED_SIZE),
        )

    override fun getMinimumSize(): Dimension = preferredSize
}
