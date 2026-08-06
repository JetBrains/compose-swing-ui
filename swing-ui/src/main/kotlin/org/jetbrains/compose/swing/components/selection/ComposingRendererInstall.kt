package org.jetbrains.compose.swing.components.selection

/**
 * Puts [renderer] onto a renderer property, given what that property currently holds as [installed] and
 * the [install] accessor that writes it: a non-null [renderer] takes the property over and holds on to
 * what it displaced, and a `null` one hands the property back to whatever was there before a composable
 * cell took it, captured at the moment it was displaced.
 *
 * This is how a renderer is put onto something that is not a component of its own - a table's column,
 * which no modifier element can name as its target. A column the table built carries no renderer at all,
 * which is what leaves its cells to the one the table picks by the column's class, and that is what
 * restoring puts back.
 *
 * Displacing a composable cell's renderer with another takes the way back with it, so the renderer left
 * installed nowhere cannot write onto anything it no longer renders.
 */
internal fun <R : Any> applyComposingRenderer(
    renderer: R?,
    installed: R?,
    install: (R?) -> Unit,
) {
    if (renderer === installed) return
    // What is installed is what the property held before a composable cell, unless a composable cell
    // already took it over - that one is the only thing still holding the way back.
    val held = (installed as? ComposingCellRenderer)?.displaced
    if (renderer == null) {
        held?.release()?.invoke()
        return
    }
    // Every caller hands in a renderer of its own cell islands, which is what a composable cell is
    // stamped through; anything else reaches this as the installed one alone.
    val incoming = renderer as ComposingCellRenderer
    incoming.displaced.adopt(held?.release() ?: { install(installed) })
    install(renderer)
}

/**
 * A renderer that stamps cells through a composable body, and holds the way back to the renderer it
 * displaced where it is the installed one.
 */
internal interface ComposingCellRenderer {
    /** The way back to the renderer this one displaced; see [applyComposingRenderer]. */
    val displaced: DisplacedRenderer
}

/**
 * The way back to the renderer a composable cell displaced where it was installed. It is armed while the
 * composable cell's renderer is the installed one and given up as that renderer is displaced, so a
 * renderer installed nowhere holds nothing that could write anywhere.
 */
internal class DisplacedRenderer {
    private var restore: () -> Unit = {}

    /** Takes over [restore] as the way back to the renderer that was displaced. */
    fun adopt(restore: () -> Unit) {
        this.restore = restore
    }

    /** Gives up the way back, leaving this holding none, and hands it to the caller to keep or to run. */
    fun release(): () -> Unit = restore.also { restore = {} }
}
