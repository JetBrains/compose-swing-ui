// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.requiredSize
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JPanel

/** The parameters [RequiredSizeFixture] varies. */
public data class RequiredSizeParameters(
    val width: Int,
    val height: Int,
)

/** A [Box] with `requiredSize` against a raw [JPanel] holding the same exact size. */
public object RequiredSizeFixture : ComposableFixture<RequiredSizeParameters, JPanel>(
    initial = RequiredSizeParameters(width = 40, height = 32),
) {
    override val alternatives: List<RequiredSizeParameters> =
        listOf(
            initial.copy(width = 72),
            initial.copy(height = 64),
        )

    @Composable
    override fun Content(parameters: RequiredSizeParameters): Unit = Content(parameters.width, parameters.height)

    /** Composes a [Box] that requires exactly [width] by [height], inside a wrapping [Box]. */
    @Composable
    public fun Content(
        width: Int,
        height: Int,
    ) {
        Box {
            Box(modifier = SwingModifier.requiredSize(width = width, height = height))
        }
    }

    /**
     * Builds the transparent raw host holding the transparent child the required size is applied to, which the
     * host's gapless layout places at its origin as the composed wrapping [Box] does.
     */
    override fun createRawSwing(): JPanel =
        transparentPanel().apply {
            layout = FlowLayout(FlowLayout.LEADING, 0, 0)
            add(transparentPanel())
        }

    /**
     * Applies the required [RequiredSizeParameters.width] and [RequiredSizeParameters.height] to the child of
     * [component].
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: RequiredSizeParameters,
    ) {
        val child = component.components.single()
        val size = Dimension(parameters.width, parameters.height)
        child.preferredSize = size
        child.minimumSize = size
        child.maximumSize = size
        component.revalidate()
    }

    private fun transparentPanel(): JPanel =
        JPanel().apply {
            isOpaque = false
            alignmentX = 0f
            alignmentY = 0f
        }
}
