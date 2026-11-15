// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.onPlaced
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JPanel

/** The parameters [OnPlacedFixture] varies: the callback that receives the placed bounds. */
public data class OnPlacedParameters(
    val onPlaced: (Rectangle) -> Unit,
)

/**
 * A [Box] reporting its placement through [SwingModifier.onPlaced] against a raw [JPanel] reporting the same bounds.
 */
public object OnPlacedFixture : ComposableFixture<OnPlacedParameters, JPanel>(
    initial = OnPlacedParameters(onPlaced = {}),
) {
    private const val WIDTH = 40
    private const val HEIGHT = 24

    /** The single-change copies of [initial], one for each changed value. */
    override val alternatives: List<OnPlacedParameters> =
        listOf(
            initial.copy(onPlaced = {}),
        )

    @Composable
    override fun Content(parameters: OnPlacedParameters): Unit = Content(parameters.onPlaced)

    /** Composes a [Box] of the fixture's size in a host [Box], reporting its placement to [onPlaced]. */
    @Composable
    public fun Content(onPlaced: (Rectangle) -> Unit) {
        Box {
            Box(modifier = SwingModifier.size(WIDTH, HEIGHT).onPlaced(onPlaced))
        }
    }

    /**
     * Builds the raw host panel with a child of the fixture's size, set through its preferred size, and the one
     * placement listener it keeps, which reports nowhere until [updateRawSwing] points it at a callback.
     */
    override fun createRawSwing(): JPanel =
        JPanel().apply {
            isOpaque = false
            alignmentX = 0f
            alignmentY = 0f
            layout = FlowLayout(FlowLayout.LEFT, 0, 0)
            add(
                JPanel().apply {
                    isOpaque = false
                    alignmentX = 0f
                    alignmentY = 0f
                    preferredSize = Dimension(WIDTH, HEIGHT)
                    addComponentListener(PlacementListener())
                },
            )
        }

    /** Points the placement listener of the child of [component] at the callback of [parameters]. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: OnPlacedParameters,
    ) {
        val listener =
            component.components
                .single()
                .componentListeners
                .filterIsInstance<PlacementListener>()
                .single()
        listener.onPlaced = parameters.onPlaced
    }

    /** A component listener that reports the component's bounds on every move and resize. */
    private class PlacementListener : ComponentAdapter() {
        var onPlaced: (Rectangle) -> Unit = {}

        override fun componentResized(event: ComponentEvent): Unit = onPlaced(event.component.bounds)

        override fun componentMoved(event: ComponentEvent): Unit = onPlaced(event.component.bounds)
    }
}
