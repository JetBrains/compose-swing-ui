// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.defaultMinSize
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The parameters [DefaultMinSizeFixture] varies: the default minima [defaultMinSize] takes,
 * where a `null` minimum is absent.
 */
public data class DefaultMinSizeParameters(
    val minWidth: Int?,
    val minHeight: Int?,
)

/**
 * A [Box] given a default minimum size by [defaultMinSize], against a raw [JPanel] whose child is given
 * the same minimum by hand.
 */
public object DefaultMinSizeFixture : ComposableFixture<DefaultMinSizeParameters, JPanel>(
    initial = DefaultMinSizeParameters(minWidth = 24, minHeight = 16),
) {
    /** The changed values: a wider minimum and a taller minimum, each a copy of [initial]. */
    override val alternatives: List<DefaultMinSizeParameters> =
        listOf(
            initial.copy(minWidth = 64),
            initial.copy(minHeight = 48),
        )

    @Composable
    override fun Content(parameters: DefaultMinSizeParameters): Unit =
        Content(minWidth = parameters.minWidth, minHeight = parameters.minHeight)

    /**
     * Composes a [Box] whose default minimum size is [minWidth] by [minHeight], inside a host [Box]
     * that supplies the constraint scope the modifier needs.
     */
    @Composable
    public fun Content(
        minWidth: Int?,
        minHeight: Int?,
    ) {
        Box {
            Box(modifier = SwingModifier.defaultMinSize(minWidth = minWidth, minHeight = minHeight))
        }
    }

    /** Builds the raw [JPanel] host: a zero-gap [FlowLayout] holding the empty child that the minimum goes on. */
    override fun createRawSwing(): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            alignmentX = 0f
            alignmentY = 0f
            add(
                JPanel().apply {
                    isOpaque = false
                    alignmentX = 0f
                    alignmentY = 0f
                },
            )
        }

    /** Declares the minima of [parameters] as the minimum and preferred size of the child of [component]. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: DefaultMinSizeParameters,
    ) {
        val size = Dimension(parameters.minWidth ?: 0, parameters.minHeight ?: 0)
        val child = component.components.single() as JComponent
        child.minimumSize = size
        child.preferredSize = size
        component.revalidate()
    }
}
