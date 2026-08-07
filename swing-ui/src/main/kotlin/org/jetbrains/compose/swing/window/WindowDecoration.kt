package org.jetbrains.compose.swing.window

/**
 * The serve/withdraw protocol a window-carried decoration ([MenuBar], [GlassPane]) shares: a window
 * carries at most one of a kind, this holds the [payload] one declaration puts on the window and the
 * [displaced] one it takes the window's place from, and [install] is how that kind puts either on a
 * [javax.swing.JRootPane].
 */
internal class WindowDecoration<T>(
    private val payload: T,
    private val displaced: T,
    private val install: (T) -> Unit,
) {
    private var serving = false

    /** Puts [payload] on the window and records this declaration as the one serving it. */
    fun serve() {
        serving = true
        install(payload)
    }

    /**
     * Hands the window back [displaced]. Does nothing unless this declaration is the one serving, so
     * withdrawing what has already been withdrawn leaves the window alone.
     */
    fun withdraw() {
        if (!serving) return
        serving = false
        install(displaced)
    }
}
