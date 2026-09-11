package org.jetbrains.compose.swing.foundation.graphics

import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D

/**
 * What a [Decoratable] component paints through: the steps its modifier declares. The library creates each value for
 * one component and writes it to [Decoratable.decoration]; a value never changes, and a change is a new value.
 *
 * Each step is a [Decorator], the first outermost: it paints at the component's *layout bounds*, which are its bounds,
 * and what it paints past them is clipped.
 */
public class Decoration internal constructor(
    /** The steps the component's modifier declares. */
    internal val steps: DecorationSteps,
    /** Whether every step answered [Decorator.isOpaque] with `true` when the library last gathered the steps. */
    internal val hasOpaqueSteps: Boolean,
) {
    /** Whether the component's modifier declares any decoration step. */
    public val isDecorated: Boolean get() = !steps.isEmpty

    /**
     * Whether [component], painted through this decoration, still covers every pixel of its bounds: `false` where a
     * step paints translucently or cuts the content ([Decorator.isOpaque]). The steps' own opacity is the one the
     * library last gathered.
     */
    @Suppress("UnusedParameter") // Every step paints at the component's bounds, so only the steps' opacity decides.
    public fun isOpaque(component: Component): Boolean = hasOpaqueSteps

    /**
     * Paints [content], the component itself in its own coordinates, through the decoration. The steps paint at the
     * layout bounds. Without a clip on [graphics], as in a capture or a print, the whole component is painted.
     *
     * @throws IllegalArgumentException where a decorated component is handed a graphics that is not a Graphics2D.
     */
    public fun paint(
        component: Component,
        graphics: Graphics,
        content: (Graphics) -> Unit,
    ) {
        val steps = steps
        if (steps.isEmpty) return content(graphics)
        require(graphics is Graphics2D) {
            "A decoration paints through Graphics2D, which is what Swing hands a component's paint; this one was " +
                "handed a ${graphics.javaClass.name}"
        }
        val decorated = graphics.create() as Graphics2D
        try {
            if (decorated.clip == null) decorated.clipRect(0, 0, component.width, component.height)
            steps.paint(decorated, component.width, component.height) { inner, _, _ ->
                val swing = inner.create()
                try {
                    content(swing)
                } finally {
                    swing.dispose()
                }
            }
        } finally {
            decorated.dispose()
        }
    }

    /** Whether ([x], [y]), in [component]'s coordinates, hits it: inside the layout bounds. */
    public fun contains(
        component: Component,
        x: Int,
        y: Int,
    ): Boolean = x in 0 until component.width && y in 0 until component.height

    /** Holds [None]. */
    public companion object {
        /** No decoration: what a component holds until the library writes one. */
        public val None: Decoration = Decoration(DecorationSteps.None, hasOpaqueSteps = true)
    }
}
