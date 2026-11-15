// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JPanel

/** The parameters [SizeFixture] varies. */
public data class SizeParameters(
    val width: Int,
    val height: Int,
)

/** A [Box] carrying [SwingModifier.size] against a raw [JPanel] with the same exact size. */
public object SizeFixture : ComposableFixture<SizeParameters, JPanel>(
    initial = SizeParameters(width = 40, height = 32),
) {
    /** The parameter changes the tests and benchmarks switch between, one per changed value. */
    override val alternatives: List<SizeParameters> =
        listOf(
            initial.copy(width = 72),
            initial.copy(height = 64),
        )

    @Composable
    override fun Content(parameters: SizeParameters): Unit =
        Content(width = parameters.width, height = parameters.height)

    /** Composes a [Box] whose size is preferred to be exactly [width] by [height]. */
    @Composable
    public fun Content(
        width: Int,
        height: Int,
    ) {
        Box {
            Box(modifier = SwingModifier.size(width = width, height = height))
        }
    }

    /** Builds the raw panel with an empty child that the exact size is applied to. */
    override fun createRawSwing(): JPanel =
        plainPanel().apply {
            alignmentX = 0f
            alignmentY = 0f
            layout = null
            add(
                plainPanel().apply {
                    alignmentX = 0f
                    alignmentY = 0f
                },
            )
        }

    /**
     * Applies [parameters] to [component], so its child's bounds match the size the modifier declares. A composed
     * [Box] keeps no preferred size of its own, so only the child's bounds carry the size.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: SizeParameters,
    ) {
        val child = component.components.single()
        child.setBounds(0, 0, parameters.width, parameters.height)
        component.revalidate()
    }

    // A composed Box is a plain JComponent that holds no look-and-feel font or colors, so its raw stand-in holds none.
    private fun plainPanel(): JPanel =
        JPanel().apply {
            font = null
            foreground = null
            background = null
            isOpaque = false
        }
}
