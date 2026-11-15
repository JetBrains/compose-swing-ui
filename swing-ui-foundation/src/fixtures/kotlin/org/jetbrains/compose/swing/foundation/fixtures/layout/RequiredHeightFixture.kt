// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.requiredHeight
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JPanel

/** The parameters [RequiredHeightFixture] varies. */
public data class RequiredHeightParameters(
    val height: Int,
)

/** A [Box] with `requiredHeight` against a raw [JPanel] holding the same exact height by hand. */
public object RequiredHeightFixture : ComposableFixture<RequiredHeightParameters, JPanel>(
    initial = RequiredHeightParameters(height = 40),
) {
    /** The alternative that changes the required height from the initial value. */
    override val alternatives: List<RequiredHeightParameters> =
        listOf(
            initial.copy(height = 80),
        )

    @Composable
    override fun Content(parameters: RequiredHeightParameters): Unit = Content(parameters.height)

    /** Composes a [Box] that requires exactly [height], inside a wrapping [Box]. */
    @Composable
    public fun Content(height: Int) {
        Box {
            Box(modifier = SwingModifier.requiredHeight(height = height))
        }
    }

    /** Creates the raw host [JPanel] wrapping the empty child the required height is applied to. */
    override fun createRawSwing(): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
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
     * Applies the required [RequiredHeightParameters.height] to the child of [component] as its preferred size, which
     * the flow layout of the wrapping host honors.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: RequiredHeightParameters,
    ) {
        val child = component.components.single()
        child.preferredSize = Dimension(0, parameters.height)
        component.revalidate()
    }
}
