// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.foundation.layout.wrapContentWidth
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JPanel
import javax.swing.OverlayLayout

/** The parameters [WrapContentWidthFixture] varies: where the child sits and whether it may exceed its parent. */
public data class WrapContentWidthParameters(
    val align: Alignment.Horizontal,
    val unbounded: Boolean,
)

/**
 * A [Box] with `wrapContentWidth` around a child wider than its fixed-size parent, against a raw [JPanel] whose
 * [OverlayLayout] places a child of the same width.
 */
public object WrapContentWidthFixture : ComposableFixture<WrapContentWidthParameters, JPanel>(
    initial = WrapContentWidthParameters(align = Alignment.Start, unbounded = false),
) {
    /** The parameter changes the tests and benchmarks switch between, one per changed value. */
    override val alternatives: List<WrapContentWidthParameters> =
        listOf(
            initial.copy(align = Alignment.CenterHorizontally),
            initial.copy(align = Alignment.End),
            initial.copy(unbounded = true),
        )

    @Composable
    override fun Content(parameters: WrapContentWidthParameters): Unit =
        Content(align = parameters.align, unbounded = parameters.unbounded)

    /** Composes a wide child wrapped by `wrapContentWidth` with [align] and [unbounded], inside a narrower parent. */
    @Composable
    public fun Content(
        align: Alignment.Horizontal,
        unbounded: Boolean,
    ) {
        Box {
            Box(modifier = SwingModifier.size(PARENT_WIDTH, HEIGHT)) {
                Box(modifier = SwingModifier.wrapContentWidth(align = align, unbounded = unbounded)) {
                    Box(modifier = SwingModifier.size(CHILD_WIDTH, HEIGHT))
                }
            }
        }
    }

    /** Builds the raw panels that stand for the host, the wrapped box and its child, laid out by [OverlayLayout]. */
    override fun createRawSwing(): JPanel =
        JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply {
            isOpaque = false
            add(
                JPanel().apply {
                    isOpaque = false
                    layout = OverlayLayout(this)
                    preferredSize = Dimension(PARENT_WIDTH, HEIGHT)
                    add(
                        JPanel(BorderLayout()).apply {
                            isOpaque = false
                            add(
                                JPanel().apply {
                                    isOpaque = false
                                    preferredSize = Dimension(CHILD_WIDTH, HEIGHT)
                                },
                            )
                        },
                    )
                },
            )
        }

    /** Sets the wrapped box's horizontal alignment from [parameters]; the host's [OverlayLayout] places it. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: WrapContentWidthParameters,
    ) {
        val host = component.getComponent(0) as JPanel
        (host.getComponent(0) as JPanel).alignmentX = alignmentX(parameters.align)
        component.revalidate()
    }

    private fun alignmentX(align: Alignment.Horizontal): Float =
        when (align) {
            Alignment.Start -> 0f
            Alignment.End -> 1f
            else -> 0.5f
        }

    /** The width of the fixed-size parent. */
    private const val PARENT_WIDTH = 180

    /** The width of the child, wider than [PARENT_WIDTH]. */
    private const val CHILD_WIDTH = 240

    /** The height of the parent and the child. */
    private const val HEIGHT = 24
}
