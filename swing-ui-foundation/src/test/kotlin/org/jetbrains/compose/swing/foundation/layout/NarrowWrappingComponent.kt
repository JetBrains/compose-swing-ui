// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import java.awt.Dimension
import javax.swing.JComponent

/**
 * A widget that prefers a 100-pixel square and lays out that area at the width it holds, so its height
 * follows its width as a wrapping text's does.
 */
public class NarrowWrappingComponent : JComponent() {
    override fun getPreferredSize(): Dimension =
        Dimension(
            100,
            100 * 100 / (if (width > 0) width else 100),
        )

    override fun getMinimumSize(): Dimension = preferredSize
}
