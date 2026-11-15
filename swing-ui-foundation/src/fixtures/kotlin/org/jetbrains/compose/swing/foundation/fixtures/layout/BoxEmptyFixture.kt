// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JPanel

/** The parameters [BoxEmptyFixture] varies. */
public data class BoxEmptyParameters(
    val width: Int,
    val height: Int,
)

/** A fixed-size [Box] with no content, against a raw [JPanel] holding the same size. */
public object BoxEmptyFixture : ComposableFixture<BoxEmptyParameters, JPanel>(
    initial = BoxEmptyParameters(width = 160, height = 100),
) {
    override val alternatives: List<BoxEmptyParameters> =
        listOf(
            initial.copy(width = 180),
            initial.copy(height = 120),
        )

    @Composable
    override fun Content(parameters: BoxEmptyParameters): Unit = Content(parameters.width, parameters.height)

    /** Composes a [Box] of [width] by [height] with no content, inside a wrapping [Box]. */
    @Composable
    public fun Content(
        width: Int,
        height: Int,
    ) {
        Box {
            Box(modifier = SwingModifier.size(width, height))
        }
    }

    /** Creates the raw [JPanel] holding the empty child the size is applied to. */
    override fun createRawSwing(): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(JPanel(null).apply { isOpaque = false })
        }

    /**
     * Applies the [BoxEmptyParameters.width] and [BoxEmptyParameters.height] to the child of [component] as its
     * preferred size, which the flow layout of [component] honors.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: BoxEmptyParameters,
    ) {
        val child = component.components.single()
        child.preferredSize = Dimension(parameters.width, parameters.height)
        component.revalidate()
    }
}
