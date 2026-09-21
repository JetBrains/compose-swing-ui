package org.jetbrains.compose.swing.foundation.layout

/**
 * A component that can answer what extent it takes under given constraints.
 *
 * Swing's child protocol carries no constraints - `getPreferredSize()` takes no argument - so a
 * container measuring a child can only coerce what that child reports. A child implementing this is
 * the exception: it is asked under the constraints its parent computed for it, and answers for them.
 *
 * Every component that [Row], [Column], [Box] and [Layout] create implements it, so a [Row] inside a [Column]
 * inside a [Box] is asked a constrained question at every step; constraints stop at a stock widget or at a
 * container from elsewhere.
 *
 * It is asked only while a container is laying its children out. A container asked what it prefers has
 * no extent to offer, so it asks its own children the same argument-less question one level down.
 */
public interface Constrainable {
    /** Measures under [constraints]; read the answer back from [constrainedWidth] and [constrainedHeight]. */
    public fun measure(constraints: Constraints)

    /** The width the last [measure] settled on. */
    public val constrainedWidth: Int

    /** The height the last [measure] settled on. */
    public val constrainedHeight: Int
}
