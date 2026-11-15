// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.height
import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JPanel

/** The parameters [HeightFixture] varies: the exact height [height] takes. */
public data class HeightParameters(
    val height: Int,
)

/** A [Box] sized by [height] against a raw [JPanel] given the same height by hand. */
public object HeightFixture : ComposableFixture<HeightParameters, JPanel>(
    initial = HeightParameters(height = 40),
) {
    override val alternatives: List<HeightParameters> =
        listOf(
            initial.copy(height = 80),
        )

    @Composable
    override fun Content(parameters: HeightParameters): Unit = Content(height = parameters.height)

    /**
     * Composes a [Box] sized by [height] inside a host [Box] that supplies the constraint scope the modifier
     * needs.
     */
    @Composable
    public fun Content(height: Int) {
        Box {
            Box(modifier = SwingModifier.height(height = height))
        }
    }

    /** Builds the raw panel with an empty child that the exact height is applied to. */
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
     * Applies [parameters] to [component], so its child's height matches the one the modifier declares, leaving width
     * free. A composed [Box] keeps no preferred, minimum or maximum size of its own, so the raw host sets none and the
     * child's bounds carry the height.
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: HeightParameters,
    ) {
        val child = component.components.single()
        child.setBounds(0, 0, 0, parameters.height)
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
