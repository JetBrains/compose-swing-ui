package org.jetbrains.compose.swing.window

/**
 * Top-left position of a window or dialog on the screen, in raw pixels.
 *
 * A position is either concrete coordinates ([Absolute]) or a request to place the window
 * ([PlatformDefault], [CenteredOnScreen], [CenteredOnOwner]). A request carries no coordinates of its
 * own, and the placement it resolves to is written back into the driving state as an [Absolute]
 * position.
 */
public sealed interface WindowPosition {
    /**
     * The window has not been placed yet, so the platform positions it (typically in a cascade
     * relative to the previously focused window).
     *
     * Meaningful only before the window is shown: once a window is visible it always has concrete
     * coordinates and can no longer return to [PlatformDefault].
     */
    public object PlatformDefault : WindowPosition {
        override fun toString(): String = "PlatformDefault"
    }

    /**
     * The window is centered on the screen, within the area the platform leaves usable by task bars
     * and menu bars.
     *
     * Declaring it again re-centers the window, whether or not it is already shown.
     */
    public object CenteredOnScreen : WindowPosition {
        override fun toString(): String = "CenteredOnScreen"
    }

    /**
     * The window is centered on the window that owns it, or on the screen when it has no owner. An
     * owner that is not on screen yet centers the window on the owner's screen instead of on the
     * owner's bounds.
     *
     * Declaring it again re-centers the window, whether or not it is already shown.
     */
    public object CenteredOnOwner : WindowPosition {
        override fun toString(): String = "CenteredOnOwner"
    }

    /**
     * Absolute top-left position in pixels relative to the screen.
     *
     * @property x the horizontal position of the window, in pixels
     * @property y the vertical position of the window, in pixels
     */
    public class Absolute(
        public val x: Int,
        public val y: Int,
    ) : WindowPosition {
        // A position is compared by its coordinates, and equals, hashCode and toString say so by hand
        // rather than by making this a data class, so that the two coordinates stay the whole of what the
        // type publishes. A data class would publish copy() and componentN() too, and both are welded to
        // this exact constructor: naming a further coordinate later would change copy()'s signature, and
        // giving x and y another order would silently change what a destructuring declaration binds.
        // Adding a member to the class as it stands does neither.
        override fun equals(other: Any?): Boolean =
            this === other || (other is Absolute && x == other.x && y == other.y)

        override fun hashCode(): Int = 31 * x + y

        override fun toString(): String = "Absolute(x=$x, y=$y)"
    }
}
