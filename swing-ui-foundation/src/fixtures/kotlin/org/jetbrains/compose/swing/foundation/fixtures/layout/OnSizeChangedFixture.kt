// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.onSizeChanged
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JPanel

/** The parameters [OnSizeChangedFixture] varies: the width of the box and the callback that receives its size. */
public data class OnSizeChangedParameters(
    val width: Int,
    val onSizeChanged: (Dimension) -> Unit,
)

/** A [Box] reporting its size through [SwingModifier.onSizeChanged] against a raw [JPanel] reporting the same size. */
public object OnSizeChangedFixture : ComposableFixture<OnSizeChangedParameters, JPanel>(
    initial = OnSizeChangedParameters(width = 40, onSizeChanged = {}),
) {
    private const val HEIGHT = 24

    /** The single-change copies of [initial], one for each changed value. */
    override val alternatives: List<OnSizeChangedParameters> =
        listOf(
            initial.copy(width = 64),
            initial.copy(onSizeChanged = {}),
        )

    @Composable
    override fun Content(parameters: OnSizeChangedParameters): Unit =
        Content(parameters.width, parameters.onSizeChanged)

    /** Composes a [Box] of [width] that reports its size to [onSizeChanged]. */
    @Composable
    public fun Content(
        width: Int,
        onSizeChanged: (Dimension) -> Unit,
    ) {
        Box {
            Box(modifier = SwingModifier.size(width, HEIGHT).onSizeChanged(onSizeChanged))
        }
    }

    /**
     * Builds the raw panel with an empty child that the exact size is applied to and the one size listener it
     * keeps, which
     * reports nowhere until [updateRawSwing] points it at a callback.
     */
    override fun createRawSwing(): JPanel =
        plainPanel().apply {
            alignmentX = 0f
            alignmentY = 0f
            layout = FlowLayout(FlowLayout.LEFT, 0, 0)
            add(
                plainPanel().apply {
                    alignmentX = 0f
                    alignmentY = 0f
                    addComponentListener(SizeListener())
                },
            )
        }

    /**
     * Sizes the child of [component] to the width of [parameters] and points its size listener at the callback of
     * [parameters].
     */
    override fun updateRawSwing(
        component: JPanel,
        parameters: OnSizeChangedParameters,
    ) {
        val child = component.components.single()
        val listener = child.componentListeners.filterIsInstance<SizeListener>().single()
        listener.onSizeChanged = parameters.onSizeChanged
        if (child.preferredSize != Dimension(parameters.width, HEIGHT)) {
            child.preferredSize = Dimension(parameters.width, HEIGHT)
            component.revalidate()
        }
    }

    // A composed Box is a plain JComponent that holds no look-and-feel font or colors, so its raw stand-in holds none.
    private fun plainPanel(): JPanel =
        JPanel().apply {
            isOpaque = false
            font = null
            foreground = null
            background = null
        }

    /** A component listener that reports the component's size after every resize. */
    private class SizeListener : ComponentAdapter() {
        var onSizeChanged: (Dimension) -> Unit = {}

        override fun componentResized(event: ComponentEvent): Unit = onSizeChanged(event.component.size)
    }
}
