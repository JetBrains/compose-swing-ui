// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.widthIn
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel

/** The parameters [WidthInFixture] varies. */
public data class WidthInParameters(
    val min: Int?,
    val max: Int?,
)

/** A [Box] carrying [SwingModifier.widthIn] against a raw [JPanel] with the same width range. */
public object WidthInFixture : ComposableFixture<WidthInParameters, JPanel>(
    initial = WidthInParameters(min = 20, max = 150),
) {
    /** The parameter changes the tests and benchmarks switch between, one per changed value. */
    override val alternatives: List<WidthInParameters> =
        listOf(
            initial.copy(min = 60),
            initial.copy(max = 100),
        )

    @Composable
    override fun Content(parameters: WidthInParameters): Unit = Content(min = parameters.min, max = parameters.max)

    /** Composes a [Box] whose width is preferred between [min] and [max], with either bound absent when `null`. */
    @Composable
    public fun Content(
        min: Int?,
        max: Int?,
    ) {
        Box {
            Box(modifier = SwingModifier.widthIn(min = min, max = max))
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

    /** Declares the width range of [parameters] as the child's minimum, preferred and maximum width. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: WidthInParameters,
    ) {
        val child = component.components.single() as JComponent
        child.minimumSize = Dimension(parameters.min ?: 0, 0)
        child.preferredSize = Dimension(parameters.min ?: 0, 0)
        child.maximumSize = Dimension(parameters.max ?: Int.MAX_VALUE, Int.MAX_VALUE)
        component.revalidate()
    }
}
