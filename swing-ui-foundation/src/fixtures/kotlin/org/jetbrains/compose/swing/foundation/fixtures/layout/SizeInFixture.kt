// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.sizeIn
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel

/** The parameters [SizeInFixture] varies: the bounds [sizeIn] takes, where a `null` bound is absent. */
public data class SizeInParameters(
    val minWidth: Int?,
    val minHeight: Int?,
    val maxWidth: Int?,
    val maxHeight: Int?,
)

/** A [Box] sized by [sizeIn] against a raw [JPanel] given the same bounds by hand. */
public object SizeInFixture : ComposableFixture<SizeInParameters, JPanel>(
    initial = SizeInParameters(minWidth = 20, minHeight = 20, maxWidth = 150, maxHeight = 150),
) {
    override val alternatives: List<SizeInParameters> =
        listOf(
            initial.copy(minWidth = 60),
            initial.copy(minHeight = 60),
            initial.copy(maxWidth = 100),
            initial.copy(maxHeight = 100),
        )

    @Composable
    override fun Content(parameters: SizeInParameters): Unit =
        Content(
            minWidth = parameters.minWidth,
            minHeight = parameters.minHeight,
            maxWidth = parameters.maxWidth,
            maxHeight = parameters.maxHeight,
        )

    /**
     * Composes a [Box] sized by [sizeIn] with the given bounds, inside a host [Box] that supplies the
     * constraint scope the modifier needs.
     */
    @Composable
    public fun Content(
        minWidth: Int?,
        minHeight: Int?,
        maxWidth: Int?,
        maxHeight: Int?,
    ) {
        Box {
            Box(
                modifier =
                    SwingModifier.sizeIn(
                        minWidth = minWidth,
                        minHeight = minHeight,
                        maxWidth = maxWidth,
                        maxHeight = maxHeight,
                    ),
            )
        }
    }

    /** Builds the raw [JPanel] host: a zero-gap [FlowLayout] holding the empty child that the size hints go on. */
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

    /** Declares the bounds of [parameters] as the minimum, preferred and maximum size of the child of [component]. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: SizeInParameters,
    ) {
        val child = component.components.single() as JComponent
        child.minimumSize = Dimension(parameters.minWidth ?: 0, parameters.minHeight ?: 0)
        child.preferredSize = Dimension(parameters.minWidth ?: 0, parameters.minHeight ?: 0)
        child.maximumSize = Dimension(parameters.maxWidth ?: Int.MAX_VALUE, parameters.maxHeight ?: Int.MAX_VALUE)
        component.revalidate()
    }
}
