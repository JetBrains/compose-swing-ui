// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.requiredWidth
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import javax.swing.JPanel

/** The parameters [RequiredWidthFixture] varies. */
public data class RequiredWidthParameters(
    val width: Int,
)

/** A [Box] with `requiredWidth` against a raw [JPanel] holding the same exact width. */
public object RequiredWidthFixture : ComposableFixture<RequiredWidthParameters, JPanel>(
    initial = RequiredWidthParameters(width = 40),
) {
    override val alternatives: List<RequiredWidthParameters> =
        listOf(
            initial.copy(width = 80),
        )

    @Composable
    override fun Content(parameters: RequiredWidthParameters): Unit = Content(parameters.width)

    /** Composes a [Box] that requires exactly [width], inside a wrapping [Box]. */
    @Composable
    public fun Content(width: Int) {
        Box {
            Box(modifier = SwingModifier.requiredWidth(width = width))
        }
    }

    /** Creates the raw host [JPanel] holding the empty child the required width is applied to. */
    override fun createRawSwing(): JPanel =
        JPanel(null).apply {
            isOpaque = false
            alignmentX = 0f
            alignmentY = 0f
            add(
                JPanel(null).apply {
                    isOpaque = false
                    alignmentX = 0f
                    alignmentY = 0f
                },
            )
        }

    /**
     * Applies the required [RequiredWidthParameters.width] to the child of [component] and sizes [component] to wrap
     * it, leaving the height at zero.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: RequiredWidthParameters,
    ) {
        val child = component.components.single()
        val size = Dimension(parameters.width, 0)
        child.preferredSize = size
        child.minimumSize = size
        child.maximumSize = size
        child.setBounds(0, 0, size.width, size.height)
        component.preferredSize = size
        component.minimumSize = size
        component.maximumSize = size
        component.revalidate()
    }
}
