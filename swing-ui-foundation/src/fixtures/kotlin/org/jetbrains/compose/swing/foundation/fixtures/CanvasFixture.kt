// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.fixtures

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.JPanel

/** The height of the surface, which no parameter varies. */
private const val SURFACE_HEIGHT = 48

/** The width and height of the oval both arms draw. */
private const val OVAL_SIZE = 24

/** The parameters [CanvasFixture] varies: the surface width, the rendering hints, and the oval's color and offset. */
public data class CanvasParameters(
    val width: Int,
    val renderingHints: Map<RenderingHints.Key, Any>?,
    val color: Color,
    val offset: Int,
)

/** A [Canvas] drawing an oval against a raw [JPanel] holding a surface that draws the same oval. */
public object CanvasFixture : ComposableFixture<CanvasParameters, JPanel>(
    initial =
        CanvasParameters(
            width = 48,
            renderingHints = mapOf(RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_OFF),
            color = Color.BLUE,
            offset = 0,
        ),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<CanvasParameters> =
        listOf(
            initial.copy(width = 64),
            initial.copy(renderingHints = mapOf(RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON)),
            initial.copy(color = Color.RED),
            initial.copy(offset = 8),
        )

    @Composable
    override fun Content(parameters: CanvasParameters): Unit =
        Content(
            parameters.width,
            parameters.renderingHints,
            parameters.color,
            parameters.offset,
        )

    /**
     * Composes a [Canvas] [width] wide with [renderingHints] that fills an oval in [color] at [offset],
     * inside a host [Box] that supplies the constraint scope the size needs.
     */
    @Composable
    public fun Content(
        width: Int,
        renderingHints: Map<RenderingHints.Key, Any>?,
        color: Color,
        offset: Int,
    ) {
        Box {
            Canvas(
                modifier = SwingModifier.size(width, SURFACE_HEIGHT),
                renderingHints = renderingHints,
            ) {
                graphics.color = color
                graphics.fillOval(offset, offset, OVAL_SIZE, OVAL_SIZE)
            }
        }
    }

    /** Builds a non-opaque [JPanel] holding the [OvalSurface], before any of the fixture's values are applied. */
    override fun createRawSwing(): JPanel =
        JPanel(null).apply {
            isOpaque = false
            add(OvalSurface())
        }

    /** Sizes [component] and its surface, and has the surface draw the oval of [parameters]. */
    override fun updateRawSwing(
        component: JPanel,
        parameters: CanvasParameters,
    ) {
        val surface = component.components.single() as OvalSurface
        val size = Dimension(parameters.width, SURFACE_HEIGHT)
        val resized = surface.size != size
        // The outer panel wraps its only child, so a hand-written program gives it the size of that child.
        component.preferredSize = size
        surface.preferredSize = size
        surface.setBounds(0, 0, size.width, size.height)
        surface.draw(parameters)
        // Only a new size is laid out; the other parameters change what the surface draws, which draw repaints.
        if (resized) component.revalidate()
    }
}

/**
 * The surface [CanvasFixture] adds to its raw panel: it draws the oval of the [CanvasParameters] last given to [draw],
 * and nothing until the first [draw].
 */
private class OvalSurface : JComponent() {
    /** The parameters of the oval drawn so far, or null before the first [draw]. */
    private var parameters: CanvasParameters? = null

    init {
        isOpaque = false
    }

    /** Draws the oval of [next] from now on, repainting only when the oval differs from the one drawn so far. */
    fun draw(next: CanvasParameters) {
        val previous = parameters
        val changed =
            previous == null ||
                next.renderingHints != previous.renderingHints ||
                next.color != previous.color ||
                next.offset != previous.offset
        parameters = next
        if (changed) repaint()
    }

    override fun paintComponent(graphics: Graphics) {
        val copy = graphics.create()
        try {
            val drawn = parameters
            if (copy is Graphics2D && drawn != null) {
                drawn.renderingHints?.let { copy.addRenderingHints(it) }
                copy.color = drawn.color
                copy.fillOval(drawn.offset, drawn.offset, OVAL_SIZE, OVAL_SIZE)
            }
        } finally {
            copy.dispose()
        }
    }
}
