// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.offset
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import javax.swing.JPanel

/** The parameters [OffsetFixture] varies: the horizontal and vertical move of the child. */
public data class OffsetParameters(
    val x: Int,
    val y: Int,
)

/** A [Box] moved by `offset` against a raw [JPanel] whose child sits at the same bounds. */
public object OffsetFixture : ComposableFixture<OffsetParameters, JPanel>(
    initial = OffsetParameters(x = 4, y = 4),
) {
    private const val CHILD_WIDTH = 240
    private const val CHILD_HEIGHT = 180

    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<OffsetParameters> =
        listOf(
            initial.copy(x = 16),
            initial.copy(y = 16),
        )

    @Composable
    override fun Content(parameters: OffsetParameters): Unit = Content(parameters.x, parameters.y)

    /** Composes a [Box] moved by [x] and [y] from where it would otherwise sit, inside a wrapping [Box]. */
    @Composable
    public fun Content(
        x: Int,
        y: Int,
    ) {
        Box {
            Box(modifier = SwingModifier.offset(x = x, y = y)) {
                Box(modifier = SwingModifier.size(CHILD_WIDTH, CHILD_HEIGHT))
            }
        }
    }

    /**
     * Creates the raw [JPanel] holding the moved panel and its child, before any offset is applied. The panel
     * declares the size of the child as its preferred size, and the moved panel gets its bounds from [updateRawSwing].
     */
    override fun createRawSwing(): JPanel =
        JPanel(null).apply {
            isOpaque = false
            preferredSize = Dimension(CHILD_WIDTH, CHILD_HEIGHT)
            add(
                JPanel(null).apply {
                    isOpaque = false
                    add(
                        JPanel(null).apply {
                            isOpaque = false
                            setBounds(0, 0, CHILD_WIDTH, CHILD_HEIGHT)
                        },
                    )
                },
            )
        }

    /** Moves the child of [component] to the offset held by [parameters], keeping its size. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: OffsetParameters,
    ) {
        component.components.single().setBounds(parameters.x, parameters.y, CHILD_WIDTH, CHILD_HEIGHT)
    }
}
