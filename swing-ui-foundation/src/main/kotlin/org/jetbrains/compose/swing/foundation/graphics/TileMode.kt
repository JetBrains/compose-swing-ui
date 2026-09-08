package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.Immutable

/**
 * What an effect reads past the edges of what it applies to. A [BlurEffect] takes [Clamp] and [Decal].
 */
@Immutable
@JvmInline
public value class TileMode private constructor(
    private val value: Int,
) {
    override fun toString(): String = if (value == 0) "Clamp" else "Decal"

    /** The treatments. */
    public companion object {
        /** The edge pixels, repeated outward. */
        @JvmStatic
        public val Clamp: TileMode = TileMode(0)

        /** Transparent black. */
        @JvmStatic
        public val Decal: TileMode = TileMode(3)
    }
}
