// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.foundation.layout.wrapContentHeight
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JPanel
import javax.swing.OverlayLayout

/** The parameters [WrapContentHeightFixture] varies: where the child sits and whether it may exceed the host. */
public data class WrapContentHeightParameters(
    val align: Alignment.Vertical,
    val unbounded: Boolean,
)

/**
 * A fixed-size host [Box] holding a taller child wrapped by [wrapContentHeight], against raw [JPanel]s placed by an
 * [OverlayLayout].
 */
public object WrapContentHeightFixture : ComposableFixture<WrapContentHeightParameters, JPanel>(
    initial = WrapContentHeightParameters(align = Alignment.Top, unbounded = false),
) {
    override val alternatives: List<WrapContentHeightParameters> =
        listOf(
            initial.copy(align = Alignment.Bottom),
            initial.copy(unbounded = true),
        )

    @Composable
    override fun Content(parameters: WrapContentHeightParameters): Unit =
        Content(align = parameters.align, unbounded = parameters.unbounded)

    /** Composes a child taller than its host, wrapped by [wrapContentHeight] with [align] and [unbounded]. */
    @Composable
    public fun Content(
        align: Alignment.Vertical,
        unbounded: Boolean,
    ) {
        Box {
            Box(modifier = SwingModifier.size(HOST_WIDTH, HOST_HEIGHT)) {
                Box(modifier = SwingModifier.wrapContentHeight(align = align, unbounded = unbounded)) {
                    Box(modifier = SwingModifier.size(CHILD_WIDTH, CHILD_HEIGHT))
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
                    preferredSize = Dimension(HOST_WIDTH, HOST_HEIGHT)
                    add(
                        JPanel(BorderLayout()).apply {
                            isOpaque = false
                            add(
                                JPanel().apply {
                                    isOpaque = false
                                    preferredSize = Dimension(CHILD_WIDTH, CHILD_HEIGHT)
                                },
                            )
                        },
                    )
                },
            )
        }

    /** Sets the wrapped box's vertical alignment from [parameters]; the host's [OverlayLayout] places it. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: WrapContentHeightParameters,
    ) {
        val host = component.getComponent(0) as JPanel
        host.getComponent(0).let { (it as JPanel).alignmentY = alignmentY(parameters.align) }
        component.revalidate()
    }

    private fun alignmentY(align: Alignment.Vertical): Float =
        when (align) {
            Alignment.Top -> 0f
            Alignment.Bottom -> 1f
            else -> 0.5f
        }

    private const val HOST_WIDTH = 180
    private const val HOST_HEIGHT = 120
    private const val CHILD_WIDTH = 40
    private const val CHILD_HEIGHT = 180
}
