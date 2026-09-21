// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.SwingComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.styleable
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JComponent

/** Test-only drawing leaf used where tests need a raw Swing view; [onDraw] draws inside the border. */
@Composable
// The trailing lambda is the drawing slot, as in androidx's Canvas, not an event.
@Suppress("LambdaParameterEventTrailing")
internal fun Canvas(
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<SwingComponentStyleScope> = SwingStyle,
    onDraw: (Graphics2D, Int, Int) -> Unit,
) {
    val currentOnDraw by rememberUpdatedState(onDraw)
    SwingNode(
        factory = {
            object : JComponent() {
                override fun paintComponent(g: Graphics) {
                    val insets = insets
                    val drawWidth = (width - insets.left - insets.right).coerceAtLeast(0)
                    val drawHeight = (height - insets.top - insets.bottom).coerceAtLeast(0)
                    val surface = g.create(insets.left, insets.top, drawWidth, drawHeight) as Graphics2D
                    try {
                        currentOnDraw(surface, drawWidth, drawHeight)
                    } finally {
                        surface.dispose()
                    }
                }
            }
        },
        modifier = modifier.styleable(style),
    )
}
