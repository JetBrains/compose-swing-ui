// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.foundation.layout.wrapContentSize
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JPanel
import javax.swing.OverlayLayout

/** The parameters [WrapContentSizeFixture] varies: where the wrapped child is placed and whether it is unbounded. */
public data class WrapContentSizeParameters(
    val align: Alignment,
    val unbounded: Boolean,
)

/**
 * Two [Box] children, one smaller and one larger than their host, wrapped by `wrapContentSize`, against a raw
 * [JPanel] whose host panel stacks two wrapping panels, each around a child panel, with an [OverlayLayout].
 */
public object WrapContentSizeFixture : ComposableFixture<WrapContentSizeParameters, JPanel>(
    initial = WrapContentSizeParameters(align = Alignment.TopStart, unbounded = false),
) {
    override val alternatives: List<WrapContentSizeParameters> =
        listOf(
            initial.copy(align = Alignment.Center),
            initial.copy(align = Alignment.BottomEnd),
            initial.copy(unbounded = true),
            initial.copy(align = Alignment.BottomEnd, unbounded = true),
        )

    @Composable
    override fun Content(parameters: WrapContentSizeParameters): Unit =
        Content(align = parameters.align, unbounded = parameters.unbounded)

    /** Composes a host [Box] holding a small and a large child, each wrapped by `wrapContentSize`. */
    @Composable
    public fun Content(
        align: Alignment,
        unbounded: Boolean,
    ) {
        Box {
            Box(modifier = SwingModifier.size(HOST_WIDTH, HOST_HEIGHT)) {
                Box(modifier = SwingModifier.wrapContentSize(align = align, unbounded = unbounded)) {
                    Box(modifier = SwingModifier.size(SMALL_WIDTH, SMALL_HEIGHT))
                }
                Box(modifier = SwingModifier.wrapContentSize(align = align, unbounded = unbounded)) {
                    Box(modifier = SwingModifier.size(LARGE_WIDTH, LARGE_HEIGHT))
                }
            }
        }
    }

    /** Builds the raw panel holding the host panel, whose [OverlayLayout] stacks a wrapping panel around each child. */
    override fun createRawSwing(): JPanel =
        JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply {
            isOpaque = false
            add(
                JPanel().apply {
                    isOpaque = false
                    layout = OverlayLayout(this)
                    preferredSize = Dimension(HOST_WIDTH, HOST_HEIGHT)
                    add(newWrapper(SMALL_WIDTH, SMALL_HEIGHT))
                    add(newWrapper(LARGE_WIDTH, LARGE_HEIGHT))
                },
            )
        }

    private fun newWrapper(
        width: Int,
        height: Int,
    ): JPanel =
        JPanel(BorderLayout()).apply {
            isOpaque = false
            add(
                JPanel().apply {
                    isOpaque = false
                    preferredSize = Dimension(width, height)
                },
            )
        }

    /** Sets the alignment of both wrapping panels of [component] from [parameters]; the host's layout places them. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: WrapContentSizeParameters,
    ) {
        val host = component.components.single() as JPanel
        val (x, y) = alignments(parameters.align)
        host.components.forEach {
            (it as JPanel).alignmentX = x
            it.alignmentY = y
        }
        component.revalidate()
    }

    private fun alignments(align: Alignment): Pair<Float, Float> =
        when (align) {
            Alignment.TopStart -> 0f to 0f
            Alignment.BottomEnd -> 1f to 1f
            else -> 0.5f to 0.5f
        }

    private const val HOST_WIDTH = 180
    private const val HOST_HEIGHT = 120
    private const val SMALL_WIDTH = 40
    private const val SMALL_HEIGHT = 24
    private const val LARGE_WIDTH = 240
    private const val LARGE_HEIGHT = 180
}
