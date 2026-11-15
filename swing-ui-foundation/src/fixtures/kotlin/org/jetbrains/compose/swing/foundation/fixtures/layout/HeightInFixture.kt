// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.heightIn
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel

/** The parameters [HeightInFixture] varies. */
public data class HeightInParameters(
    val min: Int?,
    val max: Int?,
)

/** A [Box] carrying [SwingModifier.heightIn] against a raw [JPanel] with the same height range. */
public object HeightInFixture : ComposableFixture<HeightInParameters, JPanel>(
    initial = HeightInParameters(min = 20, max = 150),
) {
    /** The parameter changes the tests and benchmarks switch between, one per changed value. */
    override val alternatives: List<HeightInParameters> =
        listOf(
            initial.copy(min = 60),
            initial.copy(max = 100),
        )

    @Composable
    override fun Content(parameters: HeightInParameters): Unit = Content(min = parameters.min, max = parameters.max)

    /** Composes a [Box] whose height is preferred between [min] and [max], with either bound absent when `null`. */
    @Composable
    public fun Content(
        min: Int?,
        max: Int?,
    ) {
        Box {
            Box(modifier = SwingModifier.heightIn(min = min, max = max))
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

    /** Declares the height range of [parameters] as the child's minimum, preferred and maximum height. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: HeightInParameters,
    ) {
        val child = component.components.single() as JComponent
        child.minimumSize = Dimension(0, parameters.min ?: 0)
        child.preferredSize = Dimension(0, parameters.min ?: 0)
        child.maximumSize = Dimension(Int.MAX_VALUE, parameters.max ?: Int.MAX_VALUE)
        component.revalidate()
    }
}
