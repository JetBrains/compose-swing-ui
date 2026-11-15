// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.absoluteOffset
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import javax.swing.JPanel

/** The parameters [AbsoluteOffsetFixture] varies: the horizontal and vertical move of the child. */
public data class AbsoluteOffsetParameters(
    val x: Int,
    val y: Int,
)

/** A [Box] moved by `absoluteOffset` against a raw [JPanel] whose child sits at the same bounds. */
public object AbsoluteOffsetFixture : ComposableFixture<AbsoluteOffsetParameters, JPanel>(
    initial = AbsoluteOffsetParameters(x = 4, y = 4),
) {
    private const val CHILD_SIZE = 40

    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<AbsoluteOffsetParameters> =
        listOf(
            initial.copy(x = 16),
            initial.copy(y = 16),
        )

    @Composable
    override fun Content(parameters: AbsoluteOffsetParameters): Unit = Content(parameters.x, parameters.y)

    /**
     * Composes a [Box] of a fixed size moved by [x] and [y] from where it would otherwise sit, inside a wrapping [Box].
     */
    @Composable
    public fun Content(
        x: Int,
        y: Int,
    ) {
        Box {
            Box(modifier = SwingModifier.absoluteOffset(x = x, y = y).size(CHILD_SIZE, CHILD_SIZE))
        }
    }

    /**
     * Creates the raw [JPanel] with its child at the origin, before any offset is applied to it. The panel declares
     * the size of the child as its preferred size, and the child is placed by `setBounds`.
     */
    override fun createRawSwing(): JPanel =
        JPanel(null).apply {
            isOpaque = false
            preferredSize = Dimension(CHILD_SIZE, CHILD_SIZE)
            add(
                JPanel(null).apply {
                    isOpaque = false
                    setBounds(0, 0, CHILD_SIZE, CHILD_SIZE)
                },
            )
        }

    /** Moves the child of [component] to the offset held by [parameters], keeping its size. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: AbsoluteOffsetParameters,
    ) {
        component.components.single().setBounds(parameters.x, parameters.y, CHILD_SIZE, CHILD_SIZE)
    }
}
